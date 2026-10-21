#include "warp.h"
#include <time.h>
#include <pthread.h>

// Timer implementation for WireGuard
// Handles keepalive, rekey, handshake timeouts, and retransmissions

typedef struct {
    void (*callback)(void* arg);
    void* arg;
    uint64_t interval_ms;
    uint64_t next_fire;
    bool repeating;
    bool active;
    struct timer_entry* next;
} timer_entry_t;

static timer_entry_t* timer_list = NULL;
static pthread_mutex_t timer_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_t timer_thread = 0;
static bool timer_thread_running = false;

// Get current time in milliseconds
static uint64_t get_time_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

// Timer thread function
static void* timer_thread_func(void* arg) {
    while (true) {
        uint64_t now = get_time_ms();

        pthread_mutex_lock(&timer_mutex);

        timer_entry_t* current = timer_list;
        timer_entry_t* prev = NULL;

        while (current) {
            if (current->active && current->next_fire <= (uint64_t)time(NULL) * 1000) {
                // Timer fired
                timer_entry_t* fired = current;

                // Remove from list
                if (prev) {
                    prev->next = current->next;
                } else {
                    timer_list = current->next;
                }
                current = current->next;

                // Execute callback outside mutex
                pthread_mutex_unlock(&timer_mutex);
                if (fired->callback) {
                    fired->callback(fired->arg);
                }
                pthread_mutex_lock(&timer_mutex);

                // Re-arm if repeating
                if (fired->repeating && fired->active) {
                    fired->next_fire = get_time_ms() + fired->interval_ms;
                    // Re-insert into list
                    fired->next = timer_list;
                    timer_list = fired;
                } else {
                    free(fired);
                }
            } else {
                prev = current;
                current = current->next;
            }
        }

        pthread_mutex_unlock(&timer_mutex);

        // Sleep for a bit
        usleep(10000); // 10ms
    }

    return NULL;
}

// Initialize timer system
int timers_init() {
    if (timer_thread_running) return 0;

    timer_thread_running = true;
    int ret = pthread_create(&timer_thread, NULL, timer_thread_func, NULL);
    if (ret != 0) {
        timer_thread_running = false;
        return -1;
    }
    pthread_detach(timer_thread);
    return 0;
}

// Stop timer system
void timers_cleanup() {
    timer_thread_running = false;
    // Timer thread will exit on next iteration
}

// Add a timer
int timer_add(void (*callback)(void*), void* arg, uint64_t interval_ms, bool repeating) {
    if (!callback) return -1;

    timer_entry_t* entry = malloc(sizeof(timer_entry_t));
    if (!entry) return -1;

    entry->callback = callback;
    entry->arg = arg;
    entry->interval_ms = interval_ms;
    entry->repeating = repeating;
    entry->active = true;
    entry->next_fire = get_time_ms() + interval_ms;
    entry->next = NULL;

    pthread_mutex_lock(&timer_mutex);
    entry->next = timer_list;
    timer_list = entry;
    pthread_mutex_unlock(&timer_mutex);

    return 0;
}

// Remove a timer
int timer_remove(void (*callback)(void*), void* arg) {
    pthread_mutex_lock(&timer_mutex);

    timer_entry_t* current = timer_list;
    timer_entry_t* prev = NULL;

    while (current) {
        if (current->callback == callback && current->arg == arg) {
            if (prev) {
                prev->next = current->next;
            } else {
                timer_list = current->next;
            }
            free(current);
            break;
        }
        prev = current;
        current = current->next;
    }

    pthread_mutex_unlock(&timer_mutex);
    return 0;
}

// Cancel a timer (deactivate but keep for potential reuse)
int timer_cancel(void (*callback)(void*), void* arg) {
    pthread_mutex_lock(&timer_mutex);

    timer_entry_t* current = timer_list;
    while (current) {
        if (current->callback == callback && current->arg == arg) {
            current->active = false;
            break;
        }
        current = current->next;
    }

    pthread_mutex_unlock(&timer_mutex);
    return 0;
}

// Update timer interval
int timer_update_interval(void (*callback)(void*), void* arg, uint64_t new_interval_ms) {
    pthread_mutex_lock(&timer_mutex);

    timer_entry_t* current = timer_list;
    while (current) {
        if (current->callback == callback && current->arg == arg) {
            current->interval_ms = new_interval_ms;
            current->next_fire = get_time_ms() + new_interval_ms;
            break;
        }
        current = current->next;
    }

    pthread_mutex_unlock(&timer_mutex);
    return 0;
}

// Get next timer fire time
uint64_t timers_next_fire() {
    pthread_mutex_lock(&timer_mutex);

    uint64_t earliest = UINT64_MAX;
    timer_entry_t* current = timer_list;
    uint64_t now = get_time_ms();

    while (current) {
        if (current->active && current->next_fire < earliest) {
            earliest = current->next_fire;
        }
        current = current->next;
    }

    pthread_mutex_unlock(&timer_mutex);

    if (earliest == UINT64_MAX) return 0;
    return earliest > now ? earliest - now : 0;
}

// Specific WireGuard timers

// Keepalive timer callback
static void keepalive_timer_cb(void* arg) {
    // Would send keepalive packet
}

// Rekey timer callback
static void rekey_timer_cb(void* arg) {
    // Would initiate rekey
}

// Handshake timeout callback
static void handshake_timeout_cb(void* arg) {
    // Would retry handshake or fail
}

// Retransmit timer callback
static void retransmit_timer_cb(void* arg) {
    // Would retransmit unacknowledged packets
}

// Initialize WireGuard-specific timers
int wireguard_timers_init(void* ctx) {
    // Initialize timer system
    timers_init();
    return 0;
}

// Start keepalive timer for a peer
int peer_start_keepalive(void* peer, uint32_t interval_sec) {
    // Would add repeating timer for keepalive
    return 0;
}

// Stop keepalive timer
void peer_stop_keepalive(void* peer) {}

// Start rekey timer
void device_start_rekey_timer(void* ctx) {}

// Start handshake timeout timer
int handshake_start_timeout_timer(void* ctx, int timeout_sec) {
    return 0;
}

// Cancel handshake timeout timer
void handshake_cancel_timeout_timer(void* ctx) {}

// Start retransmit timer
int retransmit_start_timer(void* ctx) { return 0; }

// Cancel retransmit timer
void retransmit_cancel_timer(void* ctx) {}

// Get time until next timer fire
uint64_t timers_next_fire_ms() {
    return timers_next_fire();
}

// Sleep until next timer or event
int timers_wait(int timeout_ms) {
    uint64_t next_fire = timers_next_fire_ms();
    if (next_fire == 0) return timeout_ms;
    if (next_fire < (uint64_t)timeout_ms) timeout_ms = (int)next_fire;
    usleep(timeout_ms * 1000);
    return timeout_ms;
}