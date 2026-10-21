#include "warp.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <linux/if_tun.h>
#include <linux/if.h>
#include <linux/if_ether.h>
#include <linux/ip.h>
#include <linux/ipv6.h>
#include <linux/udp.h>
#include <linux/tcp.h>

// Global context (simplified - in real implementation would be per-instance)
static warp_context_t* g_ctx = NULL;

// Forward declarations
static int warp_do_handshake(warp_context_t* ctx);
static int warp_send_handshake_initiation(warp_context_t* ctx);
static int warp_process_handshake_response(warp_context_t* ctx, const uint8_t* data, size_t len);
static int warp_derive_keys(warp_context_t* ctx);
static int warp_send_keepalive(warp_context_t* ctx);

// Error strings
static const char* error_strings[] = {
    "Success",
    "Initialization failed",
    "Start failed",
    "Stop failed",
    "Configuration error",
    "Handshake failed",
    "Crypto error",
    "Network error",
    "Memory error"
};

const char* warp_get_error_string(int error_code) {
    if (error_code >= WARP_ERROR_MEMORY && error_code <= WARP_OK) {
        return error_strings[-error_code];
    }
    return "Unknown error";
}

const char* warp_get_version(void) {
    return "WARP/1.0 (WireGuard + AmneziaWG)";
}

// Core lifecycle
int warp_init(warp_context_t* ctx) {
    if (!ctx) return WARP_ERROR_INIT;

    memset(ctx, 0, sizeof(warp_context_t));

    // Default configuration
    ctx->mtu = WARP_MTU;
    ctx->keepalive_interval = 25; // seconds
    ctx->state = WARP_STATE_STOPPED;
    ctx->is_running = false;
    ctx->is_connected = false;
    ctx->tun_fd = -1;
    ctx->send_counter = 0;
    ctx->recv_counter = 0;

    // Initialize encryption counters
    ctx->send_counter = 0;
    ctx->recv_counter = 0;

    return WARP_OK;
}

int warp_start(warp_context_t* ctx) {
    if (!ctx) return WARP_ERROR_START;
    if (ctx->is_running) return WARP_OK;

    // Create TUN interface
    int ret = warp_create_interface("tun0", ctx->mtu);
    if (ret < 0) {
        snprintf(ctx->last_error, sizeof(ctx->last_error), "Failed to create TUN interface");
        return WARP_ERROR_START;
    }
    ctx->tun_fd = ret;

    // Initiate handshake
    int ret = warp_handshake_initiate(ctx);
    if (ret != WARP_OK) {
        snprintf(ctx->last_error, sizeof(ctx->last_error), "Handshake initiation failed");
        return WARP_ERROR_HANDSHAKE;
    }

    ctx->state = WARP_STATE_CONNECTED;
    ctx->is_running = true;
    ctx->is_connected = true;
    ctx->last_handshake_time = time(NULL);

    return WARP_OK;
}

int warp_stop(warp_context_t* ctx) {
    if (!ctx) return WARP_ERROR_STOP;

    if (ctx->tun_fd >= 0) {
        close(ctx->tun_fd);
        ctx->tun_fd = -1;
    }

    ctx->is_running = false;
    ctx->is_connected = false;
    ctx->state = WARP_STATE_STOPPED;

    return WARP_OK;
}

int warp_cleanup(warp_context_t* ctx) {
    if (!ctx) return WARP_ERROR_INIT;
    warp_stop(ctx);
    memset(ctx, 0, sizeof(warp_context_t));
    return WARP_OK;
}

// Configuration
int warp_set_endpoint(warp_context_t* ctx, const char* endpoint, uint16_t port) {
    if (!ctx || !endpoint) return WARP_ERROR_CONFIG;
    strncpy(ctx->endpoint, endpoint, sizeof(ctx->endpoint) - 1);
    ctx->port = port;

    // Resolve endpoint
    struct addrinfo hints = {0}, *res;
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_DGRAM;

    char port_str[16];
    snprintf(ctx->port, sizeof(ctx->port), "%d", port);

    int ret = getaddrinfo(endpoint, port_str, &hints, &res);
    if (ret != 0) {
        return WARP_ERROR_CONFIG;
    }

    // Copy resolved address
    memcpy(&ctx->peer_addr, res->ai_addr, res->ai_addrlen);
    ctx->peer_addr_len = res->ai_addrlen;
    freeaddrinfo(res);

    return WARP_OK;
}

int warp_set_private_key(warp_context_t* ctx, const uint8_t* private_key) {
    if (!ctx || !private_key) return WARP_ERROR_CONFIG;
    memcpy(ctx->local_private, private_key, WARP_MAX_KEY_SIZE);
    // Derive public key
    return crypto_curve25519(ctx->local_public, ctx->local_private, (uint8_t*)"\x09"); // Curve25519 base point
}

int warp_set_peer_public_key(warp_context_t* ctx, const uint8_t* public_key) {
    if (!ctx || !public_key) return WARP_ERROR_CONFIG;
    memcpy(ctx->peer_public, public_key, WARP_MAX_KEY_SIZE);
    return WARP_OK;
}

int warp_set_preshared_key(warp_context_t* ctx, const uint8_t* preshared_key) {
    if (!ctx || !preshared_key) return WARP_ERROR_CONFIG;
    memcpy(ctx->preshared_key, preshared_key, WARP_MAX_KEY_SIZE);
    return WARP_OK;
}

int warp_set_keepalive(warp_context_t* ctx, uint32_t seconds) {
    if (!ctx) return WARP_ERROR_CONFIG;
    ctx->keepalive_interval = seconds;
    return WARP_OK;
}

int warp_set_mtu(warp_context_t* ctx, uint32_t mtu) {
    if (!ctx) return WARP_ERROR_CONFIG;
    if (mtu < 576 || mtu > 65535) return WARP_ERROR_CONFIG;
    ctx->mtu = mtu;
    return WARP_OK;
}

int warp_set_obfuscation(warp_context_t* ctx, bool enabled, const amnezia_obfs_params_t* params) {
    if (!ctx) return WARP_ERROR_CONFIG;
    ctx->obfuscation_enabled = enabled;
    if (params) {
        memcpy(&ctx->obfs_params, params, sizeof(amnezia_obfs_params_t));
    }
    ctx->obfuscation_enabled = enabled;
    return WARP_OK;
}

// AmneziaWG obfuscation
int amnezia_set_obfuscation_params(amnezia_obfs_params_t* params,
                                   const uint8_t* Jc, uint8_t Jmin, const uint8_t* Jmax,
                                   const uint8_t* S, const uint8_t* H, const uint8_t* I) {
    if (!params) return WARP_ERROR_CONFIG;
    if (Jc) memcpy(params->Jc, Jc, AMNEZIA_JC_LEN);
    params->Jmin = Jmin;
    if (Jmax) memcpy(params->Jmax, Jmax, AMNEZIA_JMAX_LEN);
    if (S) memcpy(params->S, S, AMNEZIA_S_LEN);
    if (H) memcpy(params->H, H, AMNEZIA_H_LEN);
    if (I) memcpy(params->I, I, AMNEZIA_I_LEN);
    return WARP_OK;
}

// Handshake
int warp_handshake(warp_context_t* ctx) {
    return warp_handshake_initiate(ctx);
}

int warp_handshake_initiate(warp_context_t* ctx) {
    if (!ctx) return WARP_ERROR_HANDSHAKE;

    // In a real implementation, this would:
    // 1. Generate ephemeral private key
    // 2. Compute shared secret with peer's public key
    // 3. Create handshake initiation message
    // 4. Send to peer
    // 5. Wait for response

    // For now, simulate successful handshake
    int ret = warp_derive_keys(ctx);
    if (ret != WARP_OK) return WARP_ERROR_HANDSHAKE;

    // Simulate successful handshake
    ctx->is_connected = true;
    ctx->state = WARP_STATE_CONNECTED;
    ctx->last_handshake_time = time(NULL);

    return WARP_OK;
}

int warp_derive_keys(warp_context_t* ctx) {
    // In a real implementation, this would derive session keys from handshake
    // Using HKDF with the shared secret
    // For now, just generate dummy keys
    memset(ctx->sending_key, 0x42, 32);
    memset(ctx->receiving_key, 0x42, 32);
    ctx->send_counter = 0;
    ctx->recv_counter = 0;
    return WARP_OK;
}

// Packet processing
int warp_encrypt_packet(warp_context_t* ctx, const uint8_t* plaintext, size_t plaintext_len,
                        uint8_t* ciphertext, size_t* ciphertext_len) {
    if (!ctx || !plaintext || !ciphertext || !ciphertext_len) return WARP_ERROR_CRYPTO;

    // Construct WireGuard packet header
    // Type (1 byte) + Reserved (3 bytes) + Key Index (4 bytes) + Counter (8 bytes)
    // Total header: 16 bytes
    size_t header_size = 16;
    size_t total_len = 16 + plaintext_len + 16; // header + plaintext + auth tag

    if (*ciphertext_len < total_len) {
        *ciphertext_len = total_len;
        return WARP_ERROR_CRYPTO;
    }

    // Build packet header
    uint8_t* out = ciphertext;
    out[0] = 4; // Packet type: Data
    memset(out + 1, 0, 3); // Reserved
    // Key index (simplified - would be derived from handshake)
    for (int i = 0; i < 4; i++) out[4 + i] = 0;
    // Counter (little endian)
    for (int i = 0; i < 8; i++) {
        out[8 + i] = (ctx->send_counter >> (i * 8)) & 0xFF;
    }

    // Encrypt payload using ChaCha20-Poly1305
    size_t out_len = 0;
    int ret = crypto_chacha20poly1305_encrypt(
        ctx->sending_key,
        (uint8_t*)&ctx->send_counter, // nonce = counter
        plaintext, plaintext_len,
        NULL, 0, // no AAD for data packets
        ciphertext + 16,
        &out_len
    );

    if (ret != WARP_OK) return WARP_ERROR_CRYPTO;

    ctx->send_counter++;
    *ciphertext_len = 16 + out_len;
    return WARP_OK;
}

int warp_decrypt_packet(warp_context_t* ctx, const uint8_t* ciphertext, size_t ciphertext_len,
                        uint8_t* plaintext, size_t* plaintext_len) {
    if (!ctx || !ciphertext || !plaintext || !plaintext_len) return WARP_ERROR_CRYPTO;

    if (ciphertext_len < 16 + 16) { // header + auth tag
        return WARP_ERROR_CRYPTO;
    }

    // Parse header
    const uint8_t* in = ciphertext;
    uint8_t packet_type = in[0];
    if (packet_type != 4) { // Not a data packet
        return WARP_ERROR_CRYPTO;
    }

    // Extract counter
    uint64_t counter = 0;
    for (int i = 0; i < 8; i++) {
        counter |= ((uint64_t)in[8 + i]) << (i * 8);
    }

    // Verify counter
    if (counter <= ctx->recv_counter) {
        return WARP_ERROR_CRYPTO; // Replay attack
    }

    // Decrypt using ChaCha20-Poly1305
    size_t plaintext_len_out = 0;
    int ret = crypto_chacha20poly1305_decrypt(
        ctx->receiving_key,
        (uint8_t*)&counter, // nonce = counter
        ciphertext + 16, ciphertext_len - 16,
        NULL, 0, // no AAD
        plaintext,
        plaintext_len
    );

    if (ret != WARP_OK) return WARP_ERROR_CRYPTO;

    ctx->recv_counter = counter + 1;
    return WARP_OK;
}

// Packet I/O
int warp_write_packet(warp_context_t* ctx, const uint8_t* packet, size_t len) {
    if (!ctx || ctx->tun_fd < 0) return WARP_ERROR_NETWORK;

    ssize_t written = write(ctx->tun_fd, packet, len);
    if (written < 0) {
        return WARP_ERROR_NETWORK;
    }

    ctx->bytes_sent += written;
    ctx->packets_sent++;
    ctx->last_sent_time = time(NULL);
    return WARP_OK;
}

int warp_read_packet(warp_context_t* ctx, uint8_t* buffer, size_t* len, int timeout_ms) {
    if (!ctx || ctx->tun_fd < 0) return WARP_ERROR_NETWORK;

    // Set timeout
    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;

    fd_set read_fds;
    FD_ZERO(&read_fds);
    FD_SET(ctx->tun_fd, &read_fds);

    int ret = select(ctx->tun_fd + 1, &read_fds, NULL, NULL, timeout_ms > 0 ? &tv : NULL);
    if (ret <= 0) {
        return (ret == 0) ? WARP_OK : WARP_ERROR_NETWORK;
    }

    ssize_t n = read(ctx->tun_fd, buffer, *len);
    if (n < 0) {
        return WARP_ERROR_NETWORK;
    }

    *len = n;
    ctx->bytes_received += n;
    ctx->packets_received++;
    ctx->last_received_time = time(NULL);
    return WARP_OK;
}

// Interface management
int warp_create_interface(const char* name, int mtu) {
    int fd = open("/dev/net/tun", O_RDWR | O_NONBLOCK);
    if (fd < 0) {
        return -1;
    }

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    ifr.ifr_flags = IFF_TUN | IFF_NO_PI;
    strncpy(ifr.ifr_name, name, IFNAMSIZ - 1);

    if (ioctl(fd, TUNSETIFF, &ifr) < 0) {
        close(fd);
        return -1;
    }

    // Set MTU
    struct ifreq ifr_mtu;
    memset(&ifr_mtu, 0, sizeof(ifr_mtu));
    strncpy(ifr_mtu.ifr_name, name, IFNAMSIZ - 1);
    ifr_mtu.ifr_mtu = mtu;

    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock >= 0) {
        ioctl(sock, SIOCSIFMTU, &ifr_mtu);
        close(sock);
    }

    // Set interface up
    struct ifreq ifr_up;
    memset(&ifr_up, 0, sizeof(ifr_up));
    strncpy(ifr_up.ifr_name, name, IFNAMSIZ - 1);
    ifr_up.ifr_flags = IFF_UP | IFF_RUNNING;

    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock >= 0) {
        ioctl(sock, SIOCSIFFLAGS, &ifr_up);
        close(sock);
    }

    // Set IP address
    struct ifreq ifr_addr;
    memset(&ifr_addr, 0, sizeof(ifr_addr));
    strncpy(ifr_addr.ifr_name, name, IFNAMSIZ - 1);
    struct sockaddr_in* addr = (struct sockaddr_in*)&ifr_addr.ifr_addr;
    addr->sin_family = AF_INET;
    addr->sin_addr.s_addr = inet_addr("10.0.0.2"); // Example

    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock >= 0) {
        ioctl(sock, SIOCSIFADDR, &ifr_addr);
        close(sock);
    }

    return fd;
}

int warp_close_interface(int fd) {
    if (fd >= 0) {
        close(fd);
    }
    return WARP_OK;
}

// Key generation
int warp_generate_keypair(uint8_t* private_key, uint8_t* public_key) {
    if (!private_key || !public_key) return WARP_ERROR_CRYPTO;

    // Generate random private key
    // In real implementation, use secure random
    for (int i = 0; i < WARP_MAX_KEY_SIZE; i++) {
        private_key[i] = rand() & 0xFF;
    }
    private_key[0] &= 0xF8; // Clamp
    private_key[31] &= 0x7F;
    private_key[31] |= 0x40;

    // Derive public key
    return crypto_curve25519(public_key, private_key, (uint8_t*)"\x09");
}

int warp_public_from_private(const uint8_t* private_key, uint8_t* public_key) {
    return crypto_curve25519(public_key, private_key, (uint8_t*)"\x09");
}

int warp_generate_preshared(uint8_t* preshared) {
    if (!preshared) return WARP_ERROR_CRYPTO;
    for (int i = 0; i < WARP_MAX_KEY_SIZE; i++) {
        preshared[i] = rand() & 0xFF;
    }
    return WARP_OK;
}

// Statistics
void warp_get_stats(void* ctx, warp_stats_t* stats) {
    if (!ctx || !stats) return;
    warp_context_t* ctx_ptr = (warp_context_t*)ctx;
    stats->bytes_sent = ctx_ptr->bytes_sent;
    stats->bytes_received = ctx_ptr->bytes_received;
    stats->packets_sent = ctx_ptr->packets_sent;
    stats->packets_received = ctx_ptr->packets_received;
    stats->last_handshake_time = ctx->last_handshake_time;
    stats->last_handshake_duration_ms = 0; // Would need to track
}

// Error strings
const char* warp_get_error_string(int error_code) {
    static const char* errors[] = {
        "Success",
        "Initialization failed",
        "Start failed",
        "Stop failed",
        "Configuration error",
        "Handshake failed",
        "Crypto error",
        "Network error",
        "Memory error"
    };

    int idx = -error_code;
    if (error_code <= WARP_OK && error_code >= WARP_ERROR_MEMORY) {
        return errors[idx];
    }
    return "Unknown error";
}