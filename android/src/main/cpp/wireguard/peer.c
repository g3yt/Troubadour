#include "warp.h"
#include <stdlib.h>
#include <string.h>

// WireGuard peer implementation
// Manages peer state, keys, endpoints, and traffic

// Peer state
#define PEER_STATE_NEW        0
#define PEER_STATE_HANDSHAKE  1
#define PEER_STATE_CONNECTED  2
#define PEER_STATE_DISCONNECTED 3

// Peer flags
#define PEER_FLAG_DYNAMIC_ENDPOINT  1
#define PEER_FLAG_REQUIRE_COOKIE    2

// Peer structure
typedef struct {
    uint8_t state;
    uint32_t flags;

    // Keys
    uint8_t public_key[32];
    uint8_t preshared_key[32];
    bool has_preshared;

    // Endpoint
    struct sockaddr_storage endpoint;
    socklen_t endpoint_len;
    bool has_endpoint;
    bool dynamic_endpoint;

    // Keys for transport
    uint8_t sending_key[32];
    uint8_t receiving_key[32];
    uint64_t send_counter;
    uint64_t recv_counter;

    // Timing
    uint64_t last_handshake_time;
    uint64_t last_sent_time;
    uint64_t last_received_time;
    uint64_t keepalive_interval;

    // Keepalive
    bool send_keepalive;

    // Cookie
    uint8_t cookie[16];
    bool has_cookie;

    // Statistics
    uint64_t bytes_sent;
    uint64_t bytes_received;
    uint64_t packets_sent;
    uint64_t packets_received;

    // Endpoint resolution
    char endpoint_host[256];
    uint16_t endpoint_port;

    // Configuration
    uint32_t keepalive_interval_sec;
    bool persistent_keepalive;
    char* allowed_ips; // Comma-separated list
    size_t allowed_ips_count;
} peer_t;

// Initialize peer
int peer_init(peer_t* peer, const uint8_t* public_key) {
    if (!peer || !public_key) return -1;
    memset(peer, 0, sizeof(peer_t));
    memcpy(peer->public_key, public_key, 32);
    peer->state = PEER_STATE_NEW;
    return 0;
}

// Set peer public key
int peer_set_public_key(peer_t* peer, const uint8_t* public_key) {
    if (!peer || !public_key) return -1;
    memcpy(peer->public_key, public_key, 32);
    return 0;
}

// Set endpoint
int peer_set_endpoint(peer_t* peer, const char* host, uint16_t port) {
    if (!peer || !host) return -1;
    strncpy(peer->endpoint_host, host, sizeof(peer->endpoint_host) - 1);
    peer->endpoint_port = port;

    // Resolve address
    struct addrinfo hints = {0}, *res;
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_DGRAM;

    char port_str[16];
    snprintf(port_str, sizeof(port_str), "%d", port);

    struct addrinfo* res;
    int ret = getaddrinfo(host, port_str, &hints, &res);
    if (ret != 0) return -1;

    memcpy(&peer->endpoint, res->ai_addr, res->ai_addrlen);
    peer->endpoint_len = res->ai_addrlen;
    peer->has_endpoint = true;
    freeaddrinfo(res);

    return 0;
}

// Update endpoint from incoming packet
int peer_update_endpoint(peer_t* peer, const struct sockaddr* addr, socklen_t len) {
    if (!peer || !addr) return -1;
    memcpy(&peer->endpoint, addr, len);
    peer->endpoint_len = len;
    peer->has_endpoint = true;
    peer->flags |= PEER_FLAG_DYNAMIC_ENDPOINT;
    return 0;
}

// Set preshared key
int peer_set_preshared(peer_t* peer, const uint8_t* preshared) {
    if (!peer) return -1;
    if (preshared) {
        memcpy(peer->preshared_key, preshared, 32);
        peer->has_preshared = true;
    } else {
        peer->has_preshared = false;
    }
    return 0;
}

// Set keepalive interval
int peer_set_keepalive(peer_t* peer, uint32_t interval_seconds) {
    if (!peer) return -1;
    peer->keepalive_interval = interval_seconds;
    peer->persistent_keepalive = interval_seconds > 0;
    return 0;
}

// Add allowed IP
int peer_add_allowed_ip(peer_t* peer, const char* cidr) {
    if (!peer || !cidr) return -1;
    // Would parse CIDR and add to allowed IPs list
    return 0;
}

// Remove allowed IP
int peer_remove_allowed_ip(peer_t* peer, const char* cidr) {
    if (!peer || !cidr) return -1;
    return 0;
}

// Check if IP is allowed
bool peer_is_allowed_ip(peer_t* peer, const struct sockaddr* addr) {
    // Would check against allowed IPs list
    return true; // For now, allow all
}

// Set transport keys
int peer_set_keys(peer_t* peer, const uint8_t* sending_key, const uint8_t* receiving_key) {
    if (!peer || !sending_key || !receiving_key) return -1;
    memcpy(peer->sending_key, sending_key, 32);
    memcpy(peer->receiving_key, receiving_key, 32);
    peer->send_counter = 0;
    peer->recv_counter = 0;
    return 0;
}

// Increment send counter
void peer_increment_send_counter(peer_t* peer) {
    if (peer) peer->send_counter++;
}

// Increment receive counter
void peer_increment_recv_counter(peer_t* peer) {
    if (peer) peer->recv_counter++;
}

// Update timing
void peer_update_timing(peer_t* peer, bool sent) {
    uint64_t now = time(NULL);
    if (sent) {
        peer->last_sent_time = time(NULL);
    } else {
        peer->last_received_time = time(NULL);
    }
}

// Check if peer needs keepalive
bool peer_needs_keepalive(peer_t* peer) {
    if (!peer->persistent_keepalive) return false;
    time_t now = time(NULL);
    return (now - peer->last_sent_time) >= peer->keepalive_interval;
}

// Get peer stats
void peer_get_stats(peer_t* peer, warp_stats_t* stats) {
    if (!peer || !stats) return;
    stats->bytes_sent = peer->bytes_sent;
    stats->bytes_received = peer->bytes_received;
    stats->packets_sent = peer->packets_sent;
    stats->packets_received = peer->packets_received;
}

// Cleanup peer
void peer_cleanup(peer_t* peer) {
    if (!peer) return;
    memset(peer, 0, sizeof(peer_t));
}