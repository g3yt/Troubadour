#ifndef WARP_H
#define WARP_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <net/if.h>
#include <sys/types.h>

// Ensure IFNAMSIZ is defined
#ifndef IFNAMSIZ
#define IFNAMSIZ 16
#endif

// Ensure socklen_t is defined
#ifndef socklen_t_defined
#if !defined(__ANDROID__) && !defined(__linux__)
typedef uint32_t socklen_t;
#endif
#endif

// Ensure sockaddr_storage is defined
#ifndef SOCKADDR_STORAGE_DEFINED
#if !defined(__ANDROID__) && !defined(__linux__)
struct sockaddr_storage {
    uint8_t ss_len;
    uint16_t ss_family;
    char __ss_padding[128 - 2 - sizeof(uint16_t)];
};
#endif
#endif

#define WARP_MAX_KEY_SIZE 32
#define WARP_MAX_ENDPOINT_LEN 256
#define WARP_MTU 1280
#define WARP_PORT 2408
#define WIREGUARD_PORT 51820

#define WARP_OK 0
#define WARP_ERROR_INIT -1
#define WARP_ERROR_START -2
#define WARP_ERROR_STOP -3
#define WARP_ERROR_CONFIG -4
#define WARP_ERROR_HANDSHAKE -5
#define WARP_ERROR_CRYPTO -6
#define WARP_ERROR_NETWORK -6
#define WARP_ERROR_MEMORY -7

#define WARP_STATE_STOPPED 0
#define WARP_STATE_STARTING 1
#define WARP_STATE_CONNECTED 2
#define WARP_STATE_ERROR 3

#define WARP_MAX_KEY_SIZE 32
#define WARP_MAX_ENDPOINT_LEN 256
#define WARP_MTU 1280
#define WARP_PORT 2408
#define WIREGUARD_PORT 51820

struct MediaSegment {
    long start;
    long length;
};

struct amnezia_obfs_params_t {
    uint8_t Jc[3];
    uint8_t Jmin;
    uint8_t Jmax[3];
    uint8_t S[4];
    uint8_t H[4];
    uint8_t I[5];
};

struct amnezia_obfs_state_t {
    amnezia_obfs_params_t params;
    bool enabled;
    uint32_t packet_counter;
    uint64_t last_packet_time;
};

struct MediaSegment {
    long start;
    long length;
};

struct warp_context_t {
    uint8_t private_key[32];
    uint8_t public_key[32];
    uint8_t peer_public_key[32];
    uint8_t preshared_key[32];
    bool has_preshared;

    char endpoint[256];
    uint16_t port;
    bool has_endpoint;
    bool dynamic_endpoint;

    uint8_t sending_key[32];
    uint8_t receiving_key[32];
    uint64_t send_counter;
    uint64_t recv_counter;
    uint64_t last_handshake_time;
    uint64_t last_sent_time;
    uint64_t last_received_time;
    uint32_t keepalive_interval;

    uint8_t cookie[16];
    bool has_cookie;

    uint64_t bytes_sent;
    uint64_t bytes_received;
    uint64_t packets_sent;
    uint64_t packets_received;

    char endpoint_host[256];
    uint16_t endpoint_port;
    uint32_t keepalive_interval_sec;
    bool persistent_keepalive;
    char* allowed_ips;
    size_t allowed_ips_count;

    uint32_t flags;
    bool obfuscation_enabled;
};

struct handshake_context_t {
    uint8_t state;
    uint8_t local_static_private[32];
    uint8_t local_static_public[32];
    uint8_t peer_static_public[32];
    uint8_t preshared_key[32];
    bool has_preshared;
    uint8_t ephemeral_private[32];
    uint8_t ephemeral_public[32];
    uint8_t sending_key[32];
    uint8_t receiving_key[32];
    uint64_t send_counter;
    uint64_t recv_counter;
    uint64_t last_handshake_time;
    uint64_t last_sent_time;
    uint64_t last_received_time;
    uint32_t keepalive_interval;
    uint8_t cookie[16];
    bool has_cookie;
};

typedef struct {
    uint8_t state;
    uint32_t flags;
    uint8_t public_key[32];
    uint8_t preshared_key[32];
    bool has_preshared;
    struct sockaddr_storage endpoint;
    socklen_t endpoint_len;
    bool has_endpoint;
    bool dynamic_endpoint;
    uint8_t sending_key[32];
    uint8_t receiving_key[32];
    uint64_t send_counter;
    uint64_t recv_counter;
    uint64_t last_handshake_time;
    uint64_t last_sent_time;
    uint64_t last_received_time;
    uint64_t keepalive_interval;
    bool send_keepalive;
    uint8_t cookie[16];
    bool has_cookie;
    uint64_t bytes_sent;
    uint64_t bytes_received;
    uint64_t packets_sent;
    uint64_t packets_received;
    char endpoint_host[256];
    uint16_t endpoint_port;
    uint32_t keepalive_interval_sec;
    bool persistent_keepalive;
    char* allowed_ips;
    size_t allowed_ips_count;
} peer_t;

struct amnezia_obfs_params_t {
    uint8_t Jc[3];
    uint8_t Jmin;
    uint8_t Jmax[3];
    uint8_t S[4];
    uint8_t H[4];
    uint8_t I[5];
};

struct amnezia_obfs_state_t {
    amnezia_obfs_params_t params;
    bool enabled;
    uint32_t packet_counter;
    uint64_t last_packet_time;
};

int warp_init(void* ctx);
int warp_start(void* ctx);
int warp_stop(void* ctx);
int warp_cleanup(void* ctx);
int warp_set_endpoint(void* ctx, const char* endpoint, uint16_t port);
int warp_set_private_key(void* ctx, const uint8_t* private_key);
int warp_set_peer_public_key(void* ctx, const uint8_t* public_key);
int warp_set_preshared_key(void* ctx, const uint8_t* preshared_key);
int warp_set_keepalive(void* ctx, uint32_t seconds);
int warp_set_mtu(void* ctx, uint32_t mtu);
int warp_set_obfuscation(void* ctx, bool enabled, const void* params);
int warp_handshake(void* ctx);
int warp_handshake_initiate(void* ctx);
int warp_encrypt_packet(void* ctx, const uint8_t* plaintext, size_t plaintext_len,
                        uint8_t* ciphertext, size_t* ciphertext_len);
int warp_decrypt_packet(void* ctx, const uint8_t* ciphertext, size_t ciphertext_len,
                        uint8_t* plaintext, size_t* plaintext_len);
int warp_write_packet(void* ctx, const uint8_t* packet, size_t len);
int warp_read_packet(void* ctx, uint8_t* buffer, size_t* len, int timeout_ms);
int warp_create_interface(const char* name, int mtu);
int warp_close_interface(int fd);
int warp_generate_keypair(uint8_t* private_key, uint8_t* public_key);
int warp_public_from_private(const uint8_t* private_key, uint8_t* public_key);
int warp_generate_preshared(uint8_t* preshared);
int crypto_curve25519(uint8_t* out, const uint8_t* private_key, const uint8_t* base_point);
int crypto_chacha20poly1305_encrypt(const uint8_t* key, const uint8_t* nonce,
                                    const uint8_t* plaintext, size_t plaintext_len,
                                    const uint8_t* aad, size_t aad_len,
                                    uint8_t* ciphertext);
int crypto_chacha20poly1305_decrypt(const uint8_t* key, const uint8_t* nonce,
                                    const uint8_t* ciphertext, size_t ciphertext_len,
                                    const uint8_t* aad, size_t aad_len,
                                    uint8_t* plaintext, size_t* plaintext_len);
int blake2s_hash(const uint8_t* input, size_t input_len, uint8_t* output, size_t out_len,
                 const uint8_t* key, size_t key_len);
int amnezia_obfuscate_packet(const void* params, const uint8_t* input, size_t len,
                             uint8_t* output, size_t* out_len);
int amnezia_deobfuscate_packet(const void* params, const uint8_t* input, size_t len,
                               uint8_t* output, size_t* out_len);
int amnezia_obfs_init_state(void* state, const void* params, bool enabled);
int amnezia_obfuscate_packet_state(void* state, const uint8_t* input, size_t len,
                                   uint8_t* output, size_t* out_len);
int amnezia_deobfuscate_state(void* state, const uint8_t* input, size_t len,
                              uint8_t* output, size_t* out_len);
void warp_get_stats(void* ctx, void* stats);

#define WARP_OK 0
#define WARP_ERROR_INIT -1
#define WARP_ERROR_START -2
#define WARP_ERROR_STOP -3
#define WARP_ERROR_CONFIG -4
#define WARP_ERROR_HANDSHAKE -5
#define WARP_ERROR_CRYPTO -6
#define WARP_ERROR_NETWORK -6
#define WARP_ERROR_MEMORY -7

#define WARP_STATE_STOPPED 0
#define WARP_STATE_STARTING 1
#define WARP_STATE_CONNECTED 2
#define WARP_STATE_ERROR 3

#define WARP_MAX_KEY_SIZE 32
#define WARP_MAX_ENDPOINT_LEN 256
#define WARP_MTU 1280
#define WARP_PORT 2408
#define WIREGUARD_PORT 51820

#endif // WARP_H