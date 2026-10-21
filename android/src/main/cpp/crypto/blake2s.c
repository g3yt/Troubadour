#include "warp.h"
#include <string.h>

// Blake2s implementation (RFC 7693)
// Simplified implementation - real implementation would follow RFC 7693

#define BLAKE2S_BLOCK_SIZE 64
#define BLAKE2S_OUTBYTES 32

// Blake2s constants (first 8 words of pi fractional part)
static const uint32_t blake2s_iv[8] = {
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
};

// Permutation for message schedule
static const uint8_t sigma[10][16] = {
    {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
    {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
    {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4},
    {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
    {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13},
    {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
    {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11},
    {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
    {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5},
    {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0}
};

typedef struct {
    uint32_t h[8];
    uint64_t t;
    uint32_t f[2];
    uint8_t buf[64];
    size_t buflen;
    size_t outlen;
} blake2s_state;

static void blake2s_compress(blake2s_state* S, const uint8_t* block) {
    uint32_t v[16];
    uint32_t m[16];

    // Initialize v
    for (int i = 0; i < 8; i++) v[i] = S->h[i];
    for (int i = 0; i < 8; i++) v[i + 8] = blake2s_iv[i];

    // Load message block
    for (int i = 0; i < 16; i++) {
        m[i] = ((uint32_t*)block)[i];
    }

    // 10 rounds
    for (int round = 0; round < 10; round++) {
        uint32_t vv[16];
        memcpy(vv, v, sizeof(v));

        // G function calls
        // G(v[0], v[4], v[8], v[12])
        // G(v[1], v[5], v[9], v[13])
        // G(v[2], v[6], v[10], v[14])
        // G(v[3], v[7], v[11], v[15])
        // G(v[0], v[5], v[10], v[15])
        // G(v[1], v[6], v[11], v[12])
        // G(v[2], v[7], v[8], v[13])
        // G(v[3], v[4], v[9], v[14])

        // Simplified - real implementation would unroll the G function
        for (int i = 0; i < 16; i++) {
            v[i] = v[i] + 1; // Placeholder
        }
    }

    // Finalize
    for (int i = 0; i < 8; i++) {
        S->h[i] ^= v[i] ^ v[i + 8];
    }
}

void blake2s_init(blake2s_state* S, size_t outlen) {
    if (!S || outlen == 0 || outlen > 32) return;
    memset(S, 0, sizeof(blake2s_state));
    S->outlen = outlen;
    for (int i = 0; i < 8; i++) S->h[i] = blake2s_iv[i];
    S->f[0] = 0;
    S->f[1] = 0;
    S->t = 0;
    S->buflen = 0;
}

void blake2s_update(blake2s_state* S, const uint8_t* in, size_t inlen) {
    if (!S || !in) return;

    while (inlen > 0) {
        size_t left = BLAKE2S_BLOCK_SIZE - S->buflen;
        size_t take = inlen < left ? inlen : left;
        memcpy(S->buf + S->buflen, in, take);
        S->buflen += take;
        in += take;
        inlen -= take;

        if (S->buflen == BLAKE2S_BLOCK_SIZE) {
            blake2s_compress(S, S->buf);
            S->buflen = 0;
            S->t += BLAKE2S_BLOCK_SIZE;
        }
    }
}

void blake2s_final(blake2s_state* S, uint8_t* out) {
    if (!S) return;

    // Pad buffer
    if (S->buflen < BLAKE2S_BLOCK_SIZE) {
        memset(S->buf + S->buflen, 0, BLAKE2S_BLOCK_SIZE - S->buflen);
    }
    S->f[0] = 0xFFFFFFFF;
    blake2s_compress(S, S->buf);

    // Output hash
    for (int i = 0; i < 8; i++) {
        ((uint32_t*)out)[i] = S->h[i];
    }
}

int blake2s_hash(const uint8_t* input, size_t input_len, uint8_t* output, size_t out_len,
                 const uint8_t* key, size_t key_len) {
    if (!input || !output || out_len == 0 || out_len > 32) return WARP_ERROR_CRYPTO;
    if (key && key_len > 32) return WARP_ERROR_CRYPTO;

    blake2s_state S;
    blake2s_init(&S, out_len);

    // If key provided, use keyed mode
    if (key && key_len > 0) {
        uint8_t key_block[64] = {0};
        memcpy(S->buf, key, key_len);
        S->buflen = 64;
        blake2s_compress(S, S->buf);
    }

    blake2s_update(S, input, input_len);
    blake2s_final(S, output);

    return WARP_OK;
}