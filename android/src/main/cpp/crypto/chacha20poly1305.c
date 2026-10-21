#include "warp.h"
#include <string.h>

static const uint32_t chacha20_constants[4] = {0x61707865, 0x3320646e, 0x79622d32, 0x6b206574};

#define ROTL32(v, n) (((v) << (n)) | ((v) >> (32 - (n))))

#define QR(a, b, c, d) \
    a += b; d ^= a; d = ROTL32(d, 16); \
    c += d; b ^= c; b = ROTL32(b, 12); \
    a += b; d ^= a; d = ROTL32(d, 8);  \
    c += d; b ^= c; b = ROTL32(b, 7);

void chacha20_block(uint32_t state[16], uint32_t out[16]) {
    uint32_t x[16];
    for (int i = 0; i < 16; i++) x[i] = state[i];

    for (int i = 0; i < 10; i++) {
        QR(x[0], x[4], x[8], x[12]);
        QR(x[1], x[5], x[9], x[13]);
        QR(x[2], x[6], x[10], x[14]);
        QR(x[3], x[7], x[11], x[15]);
        QR(x[0], x[5], x[10], x[15]);
        QR(x[1], x[6], x[11], x[12]);
        QR(x[2], x[7], x[8], x[13]);
        QR(x[3], x[4], x[9], x[14]);
    }

    for (int i = 0; i < 16; i++) out[i] = state[i] + x[i];
}

typedef struct {
    uint32_t state[16];
} chacha20_ctx;

void chacha20_init(chacha20_ctx* ctx, const uint8_t key[32], const uint8_t nonce[12], uint32_t counter) {
    ctx->state[0] = 0x61707865;
    ctx->state[1] = 0x3320646e;
    ctx->state[2] = 0x79622d32;
    ctx->state[3] = 0x6b206574;
    for (int i = 0; i < 8; i++) ctx->state[4 + i] = ((uint32_t*)key)[i];
    ctx->state[12] = counter;
    ctx->state[13] = ((uint32_t*)nonce)[0];
    ctx->state[14] = ((uint32_t*)nonce)[1];
    ctx->state[15] = ((uint32_t*)nonce)[2];
}

void chacha20_encrypt(const uint8_t* key, const uint8_t nonce[12], uint32_t counter,
                      const uint8_t* plaintext, size_t len, uint8_t* ciphertext) {
    chacha20_ctx ctx;
    chacha20_init(&ctx, key, nonce, counter);

    uint8_t keystream[64];
    size_t offset = 0;
    uint32_t block_counter = counter;
    while (offset < len) {
        uint32_t block_state[16];
        memcpy(block_state, ctx.state, 64);
        block_state[12] = block_counter++;
        uint32_t keystream_words[16];
        chacha20_block(block_state, keystream_words);
        for (int i = 0; i < 16; i++) ((uint32_t*)keystream)[i] = keystream_words[i];
        size_t chunk = len - offset < 64 ? len - offset : 64;
        for (size_t i = 0; i < chunk; i++) {
            ciphertext[offset + i] = keystream[i] ^ plaintext[offset + i];
        }
        offset += chunk;
    }
}

int chacha20poly1305_encrypt(const uint8_t* key, const uint8_t* nonce,
                             const uint8_t* plaintext, size_t plaintext_len,
                             const uint8_t* aad, size_t aad_len,
                             uint8_t* ciphertext, size_t* ciphertext_len) {
    // Generate poly1305 key from ChaCha20 with counter=0
    uint8_t poly_key[32];
    {
        uint32_t state[16];
        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;
        for (int i = 0; i < 8; i++) state[4 + i] = ((uint32_t*)key)[i];
        state[12] = 0; state[13] = ((uint32_t*)nonce)[0];
        state[14] = ((uint32_t*)nonce)[1];
        state[15] = ((uint32_t*)nonce)[2];

        uint32_t out[16];
        chacha20_block(state, out);
        for (int i = 0; i < 8; i++) {
            ((uint32_t*)poly_key)[i] = out[i];
        }
    }

    if (*ciphertext_len < plaintext_len + 16) {
        *ciphertext_len = plaintext_len + 16;
        return -1;
    }

    // Encrypt plaintext
    chacha20_encrypt(key, nonce, 1, plaintext, plaintext_len, ciphertext);

    // Compute Poly1305 tag
    // Poly1305 key is first 32 bytes of ChaCha20 stream with counter=0
    // We already have poly_key from above

    // Compute Poly1305 tag over ciphertext + padding + aad + padding + lengths
    // Simplified implementation - in real code would use proper Poly1305
    uint8_t tag[16];
    memset(tag, 0xAA, 16); // Placeholder

    // Copy ciphertext and append tag
    memcpy(ciphertext + plaintext_len, tag, 16);
    *ciphertext_len = plaintext_len + 16;

    return 0;
}

int chacha20poly1305_decrypt(const uint8_t* key, const uint8_t* nonce,
                             const uint8_t* ciphertext, size_t ciphertext_len,
                             const uint8_t* aad, size_t aad_len,
                             uint8_t* plaintext, size_t* plaintext_len) {
    if (ciphertext_len < 16) return -1;

    size_t plaintext_len_needed = ciphertext_len - 16;

    // Verify tag (simplified - placeholder)
    // In real implementation, would verify Poly1305 tag

    // Decrypt
    // Placeholder - would decrypt with ChaCha20
    return -1;
}