#include "warp.h"
#include <string.h>
#include <stdlib.h>

// AmneziaWG obfuscation implementation
// Based on AmneziaWG protocol specification

// Obfuscation parameters structure
typedef struct {
    uint8_t Jc[3];
    uint8_t Jmin;
    uint8_t Jmax[3];
    uint8_t S[4];
    uint8_t H[4];
    uint8_t I[5];
} amnezia_obfs_params_t;

// Obfuscation state
typedef struct {
    amnezia_obfs_params_t params;
    bool enabled;
    uint32_t packet_counter;
    uint64_t last_packet_time;
} amnezia_obfs_state_t;

// Initialize obfuscation state
int amnezia_obfs_init(amnezia_obfs_state_t* state, const amnezia_obfs_params_t* params, bool enabled) {
    if (!state) return -1;
    memset(state, 0, sizeof(amnezia_obfs_state_t));
    if (params) {
        memcpy(&state->params, params, sizeof(amnezia_obfs_params_t));
    }
    state->enabled = enabled;
    return 0;
}

// Obfuscate a packet
// AmneziaWG obfuscation adds junk bytes and modifies packet structure
// to evade DPI detection
int amnezia_obfuscate_packet(const amnezia_obfs_params_t* params, const uint8_t* input, size_t len,
                             uint8_t* output, size_t* out_len) {
    if (!params || !input || !output || !out_len) return -1;

    // AmneziaWG obfuscation adds junk bytes before the actual packet
    // Format: [Jc random bytes][Jmin][Jmax...][S bytes][H bytes][I bytes][original packet]

    size_t junk_len = 0;
    // Jc: number of junk bytes at start
    uint8_t jc = params->Jc[0] + (params->Jc[1] << 8) + (params->Jc[2] << 16);
    jc = jc % 64; // Limit junk bytes

    // Jmin: minimum junk between packets
    uint8_t jmin = params->Jmin;

    // Jmax: max junk variation
    uint8_t jmax_val = params->Jmax[0] + (params->Jmax[1] << 8) + (params->Jmax[2] << 16);

    // Calculate junk length
    size_t junk_len = jc + (rand() % (jmax_val + 1 - jmin)) + jmin;

    // Check output buffer size
    if (junk_len + len > *out_len) {
        return -1; // Buffer too small
    }

    // Generate junk bytes
    for (size_t i = 0; i < junk_len; i++) {
        output[i] = rand() & 0xFF;
    }

    // Add S bytes (fixed pattern)
    for (int i = 0; i < 4; i++) {
        output[junk_len + i] = params->S[i];
    }

    // Add H bytes
    for (int i = 0; i < 4; i++) {
        output[junk_len + 4 + i] = params->H[i];
    }

    // Add I bytes
    for (int i = 0; i < 5; i++) {
        output[junk_len + 8 + i] = params->I[i];
    }

    // Copy original packet
    memcpy(output + junk_len + 13, input, len);
    *out_len = junk_len + 13 + len;

    return 0;
}

// Deobfuscate a packet
int amnezia_deobfuscate_packet(const amnezia_obfs_params_t* params, const uint8_t* input, size_t len,
                               uint8_t* output, size_t* out_len) {
    if (!params || !input || !output || !out_len || len < 13) return -1;

    // Parse obfuscation header
    // Skip junk bytes (Jc)
    // Find S, H, I markers to locate actual packet

    // Simplified implementation: assume fixed header structure
    if (len < 13) return -1;

    // Skip junk bytes - in real implementation, would scan for S/H/I markers
    // For now, assume fixed header of 13 bytes
    size_t header_len = 13;

    if (len <= header_len) return -1;

    size_t payload_len = len - header_len;
    memcpy(output, input + header_len, payload_len);
    *out_len = payload_len;

    return 0;
}

// Initialize obfuscation state
int amnezia_obfs_init_state(amnezia_obfs_state_t* state, const amnezia_obfs_params_t* params, bool enabled) {
    if (!state) return -1;
    memset(state, 0, sizeof(amnezia_obfs_state_t));
    if (params) memcpy(&state->params, params, sizeof(amnezia_obfs_params_t));
    state->enabled = enabled;
    state->packet_counter = 0;
    return 0;
}

// Obfuscate packet with state tracking
int amnezia_obfuscate_packet_state(amnezia_obfs_state_t* state, const uint8_t* input, size_t len,
                                   uint8_t* output, size_t* out_len) {
    if (!state || !state->enabled) return -1;

    // Update packet counter
    state->packet_counter++;

    // Add timing variation (Jmin/Jmax)
    // In a real implementation, would add variable delays

    return amnezia_obfuscate_packet(&state->params, input, len, output, out_len);
}

// Deobfuscate with state
int amnezia_deobfuscate_state(amnezia_obfs_state_t* state, const uint8_t* input, size_t len,
                              uint8_t* output, size_t* out_len) {
    if (!state || !state->enabled) return -1;
    return amnezia_deobfuscate_packet(&state->params, input, len, output, out_len);
}