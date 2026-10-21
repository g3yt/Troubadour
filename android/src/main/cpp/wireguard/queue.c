#include "warp.h"
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

// Packet queue implementation for WireGuard
// Thread-safe circular buffer for packet queues

#define DEFAULT_QUEUE_SIZE 1024

// Queue element
typedef struct queue_entry {
    uint8_t* data;
    size_t len;
    uint64_t timestamp;
    struct queue_entry* next;
} queue_entry_t;

// Queue structure
typedef struct {
    pthread_mutex_t mutex;
    queue_entry_t* head;
    queue_entry_t* tail;
    size_t count;
    size_t max_size;
    size_t max_bytes;
    size_t current_bytes;
    bool blocking;
} packet_queue_t;

// Initialize queue
packet_queue_t* queue_create(size_t max_size, size_t max_bytes) {
    packet_queue_t* q = calloc(1, sizeof(packet_queue_t));
    if (!q) return NULL;

    q->max_size = max_size ? max_size : DEFAULT_QUEUE_SIZE;
    q->max_bytes = max_bytes ? max_bytes : 1024 * 1024; // 1MB default
    q->blocking = false;
    pthread_mutex_init(&q->mutex, NULL);
    return q;
}

// Destroy queue
void queue_destroy(packet_queue_t* q) {
    if (!q) return;

    pthread_mutex_lock(&q->mutex);
    while (q->head) {
        queue_entry_t* entry = q->head;
        q->head = entry->next;
        free(entry->data);
        free(entry);
    }
    pthread_mutex_unlock(&q->mutex);
    pthread_mutex_destroy(&q->mutex);
    free(q);
}

// Push packet to queue tail
int queue_push(packet_queue_t* q, const uint8_t* data, size_t len) {
    if (!q || !data || len == 0) return -1;

    queue_entry_t* entry = malloc(sizeof(queue_entry_t) + len);
    if (!entry) return -1;

    entry->len = len;
    entry->timestamp = time(NULL);
    entry->next = NULL;
    memcpy((uint8_t*)(entry + 1), data, len);

    pthread_mutex_lock(&q->mutex);

    // Check size limits
    if (q->count >= q->max_size || q->current_bytes + len > q->max_bytes) {
        pthread_mutex_unlock(&q->mutex);
        free(entry);
        return -1; // Queue full
    }

    // Add to tail
    if (q->tail) {
        q->tail->next = entry;
    } else {
        q->head = entry;
    }
    q->tail = entry;
    q->count++;
    q->current_bytes += len;

    pthread_mutex_unlock(&q->mutex);
    return 0;
}

// Pop packet from queue head
int queue_pop(packet_queue_t* q, uint8_t** out_data, size_t* out_len) {
    if (!q || !out_data || !out_len) return -1;

    pthread_mutex_lock(&q->mutex);

    if (!q->head) {
        pthread_mutex_unlock(&q->mutex);
        return -1; // Empty
    }

    queue_entry_t* entry = q->head;
    q->head = entry->next;
    if (!q->head) q->tail = NULL;

    q->count--;
    q->current_bytes -= entry->len;

    pthread_mutex_unlock(&q->mutex);

    *out_data = (uint8_t*)(entry + 1);
    *out_len = entry->len;
    // Note: caller must free the entry after use
    return 0;
}

// Peek at head without removing
int queue_peek(packet_queue_t* q, const uint8_t** out_data, size_t* out_len) {
    if (!q || !out_data || !out_len) return -1;

    pthread_mutex_lock(&q->mutex);
    if (!q->head) {
        pthread_mutex_unlock(&q->mutex);
        return -1;
    }

    queue_entry_t* entry = q->head;
    *out_data = (uint8_t*)(entry + 1);
    *out_len = entry->len;

    pthread_mutex_unlock(&q->mutex);
    return 0;
}

// Get queue size
size_t queue_size(packet_queue_t* q) {
    if (!q) return 0;
    pthread_mutex_lock(&q->mutex);
    size_t count = q->count;
    pthread_mutex_unlock(&q->mutex);
    return count;
}

// Get current bytes in queue
size_t queue_bytes(packet_queue_t* q) {
    if (!q) return 0;
    pthread_mutex_lock(&q->mutex);
    size_t bytes = q->current_bytes;
    pthread_mutex_unlock(&q->mutex);
    return bytes;
}

// Check if empty
bool queue_empty(packet_queue_t* q) {
    if (!q) return true;
    pthread_mutex_lock(&q->mutex);
    bool empty = (q->count == 0);
    pthread_mutex_unlock(&q->mutex);
    return empty;
}

// Check if full
bool queue_full(packet_queue_t* q) {
    if (!q) return true;
    pthread_mutex_lock(&q->mutex);
    bool full = (q->count >= q->max_size) || (q->current_bytes >= q->max_bytes);
    pthread_mutex_unlock(&q->mutex);
    return full;
}

// Clear queue
void queue_clear(packet_queue_t* q) {
    if (!q) return;

    pthread_mutex_lock(&q->mutex);
    while (q->head) {
        queue_entry_t* entry = q->head;
        q->head = entry->next;
        free(entry->data);
        free(entry);
    }
    q->head = q->tail = NULL;
    q->count = 0;
    q->current_bytes = 0;
    pthread_mutex_unlock(&q->mutex);
}

// Set blocking mode
void queue_set_blocking(packet_queue_t* q, bool blocking) {
    if (!q) return;
    pthread_mutex_lock(&q->mutex);
    q->blocking = blocking;
    pthread_mutex_unlock(&q->mutex);
}

// Pop with timeout (blocking)
int queue_pop_timeout(packet_queue_t* q, uint8_t** out_data, size_t* out_len, int timeout_ms) {
    if (!q || !out_data || !out_len) return -1;

    uint64_t deadline = 0;
    if (timeout_ms >= 0) {
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        deadline = ts.tv_sec * 1000 + ts.tv_nsec / 1000000 + timeout_ms;
    }

    while (true) {
        int ret = queue_pop(q, out_data, out_len);
        if (ret == 0) return 0;

        if (timeout_ms < 0) {
            // Blocking wait - wait a bit and retry
            usleep(1000);
            continue;
        }

        if (timeout_ms == 0) return -1; // Non-blocking

        uint64_t now = time(NULL) * 1000;
        // Would need proper time measurement
        usleep(1000);
    }
}

// Get queue stats
void queue_get_stats(packet_queue_t* q, size_t* count, size_t* bytes) {
    if (!q) return;
    pthread_mutex_lock(&q->mutex);
    if (count) *count = q->count;
    if (bytes) *bytes = q->current_bytes;
    pthread_mutex_unlock(&q->mutex);
}

// Specialized packet queue for WireGuard
typedef struct {
    packet_queue_t base;
    // Additional WireGuard-specific fields
    uint64_t max_packet_size;
} wg_packet_queue_t;

wg_packet_queue_t* wg_queue_create(size_t max_packets) {
    wg_packet_queue_t* q = calloc(1, sizeof(wg_packet_queue_t));
    if (!q) return NULL;
    q->base = *queue_create(max_size, 1024 * 1024);
    q->max_packet_size = 2048;
    return q;
}

void wg_queue_destroy(wg_packet_queue_t* q) {
    if (q) queue_destroy((packet_queue_t*)q);
    free(q);
}

// Packet pool for zero-copy
typedef struct packet_pool {
    pthread_mutex_t mutex;
    uint8_t** buffers;
    size_t count;
    size_t capacity;
    size_t buffer_size;
} packet_pool_t;

packet_pool_t* packet_pool_create(size_t capacity, size_t buffer_size) {
    packet_pool_t* pool = calloc(1, sizeof(packet_pool_t));
    if (!pool) return NULL;

    pool->capacity = capacity;
    pool->buffer_size = buffer_size;
    pool->buffers = calloc(capacity, sizeof(uint8_t*));
    if (!pool->buffers) {
        free(pool);
        return NULL;
    }

    // Pre-allocate buffers
    for (size_t i = 0; i < capacity; i++) {
        pool->buffers[i] = malloc(buffer_size);
        if (!pool->buffers[i]) {
            // Cleanup on failure
            for (size_t j = 0; j < i; j++) free(pool->buffers[j]);
            free(pool->buffers);
            free(pool);
            return NULL;
        }
    }

    pthread_mutex_init(&pool->mutex, NULL);
    pool->count = capacity;
    return pool;
}

void packet_pool_destroy(packet_pool_t* pool) {
    if (!pool) return;
    for (size_t i = 0; i < pool->capacity; i++) {
        free(pool->buffers[i]);
    }
    free(pool->buffers);
    pthread_mutex_destroy(&pool->mutex);
    free(pool);
}

uint8_t* packet_pool_alloc(packet_pool_t* pool) {
    if (!pool) return NULL;
    pthread_mutex_lock(&pool->mutex);
    if (pool->count == 0) {
        pthread_mutex_unlock(&pool->mutex);
        return NULL;
    }
    uint8_t* buf = pool->buffers[--pool->count];
    pthread_mutex_unlock(&pool->mutex);
    return buf;
}

void packet_pool_free(packet_pool_t* pool, uint8_t* buffer) {
    if (!pool || !buffer) return;
    pthread_mutex_lock(&pool->mutex);
    if (pool->count < pool->capacity) {
        pool->buffers[pool->count++] = buffer;
    } else {
        free(buffer); // Pool full, just free
    }
    pthread_mutex_unlock(&pool->mutex);
}