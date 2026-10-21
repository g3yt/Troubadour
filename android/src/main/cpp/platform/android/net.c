#include "warp.h"
#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/ioctl.h>
#include <linux/if_tun.h>
#include <linux/if.h>
#include <linux/if_ether.h>
#include <linux/ip.h>
#include <linux/ipv6.h>
#include <linux/udp.h>
#include <linux/tcp.h>

// Android-specific network implementation for WARP/WireGuard

// Get default route interface
int android_get_default_interface(char* ifname, size_t len) {
    FILE* fp = fopen("/proc/net/route", "r");
    if (!fp) return -1;

    char line[256];
    while (fgets(line, sizeof(line), fp)) {
        char iface[IFNAMSIZ];
        unsigned long dest, gateway, flags, refcnt, use, metric, mask, mtu, window, irtt;
        if (sscanf(line, "%s %lx %lx %lx %lx %lx %lx %lx %lx %lx %lx",
                   iface, &dest, &gateway, &flags, &refcnt, &use,
                   &metric, &mask, &mtu, &window, &irtt) == 11) {
            if (dest == 0 && (flags & 0x2)) { // Default route (RTF_GATEWAY)
                strncpy(ifname, iface, len - 1);
                ifname[len - 1] = '\0';
                fclose(fp);
                return 0;
            }
        }
    }
    fclose(fp);
    return -1;
}

// Get interface IP address
int android_get_interface_ip(const char* ifname, char* ip, size_t len) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -1;

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);

    if (ioctl(fd, SIOCGIFADDR, &ifr) < 0) {
        close(fd);
        return -1;
    }
    close(fd);

    struct sockaddr_in* addr = (struct sockaddr_in*)&ifr.ifr_addr;
    inet_ntop(AF_INET, &addr->sin_addr, ip, len);
    return 0;
}

// Get interface MTU
int android_get_interface_mtu(const char* ifname) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -1;

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);

    if (ioctl(fd, SIOCGIFMTU, &ifr) < 0) {
        close(fd);
        return -1;
    }
    close(fd);
    return ifr.ifr_mtu;
}

// Check if interface is up
bool android_is_interface_up(const char* ifname) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return false;

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);

    int ret = ioctl(fd, SIOCGIFFLAGS, &ifr);
    close(fd);

    if (ret < 0) return false;
    return (ifr.ifr_flags & IFF_UP) && (ifr.ifr_flags & IFF_RUNNING);
}

// Get interface flags
int android_get_interface_flags(const char* ifname, int* flags) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -1;

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);

    int ret = ioctl(fd, SIOCGIFFLAGS, &ifr);
    close(fd);

    if (ret < 0) return -1;
    *flags = ifr.ifr_flags;
    return 0;
}

// Create TUN interface
int android_create_tun_interface(const char* name, int mtu) {
    int fd = open("/dev/net/tun", O_RDWR | O_NONBLOCK);
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "WarpNet", "Failed to open /dev/net/tun: %s", strerror(errno));
        return -1;
    }

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    ifr.ifr_flags = IFF_TUN | IFF_NO_PI;
    strncpy(ifr.ifr_name, name, IFNAMSIZ - 1);

    if (ioctl(fd, TUNSETIFF, &ifr) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "WarpNet", "TUNSETIFF failed: %s", strerror(errno));
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

    // Bring interface up
    struct ifreq ifr_up;
    memset(&ifr_up, 0, sizeof(ifr_up));
    strncpy(ifr_up.ifr_name, name, IFNAMSIZ - 1);
    ifr_up.ifr_flags = IFF_UP | IFF_RUNNING;

    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock >= 0) {
        ioctl(sock, SIOCSIFFLAGS, &ifr_up);
        close(sock);
    }

    // Set IP address (would be configured based on WireGuard config)
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

    // Add route for VPN traffic
    // This would be done via netlink or ip command

    return fd;
}

// Close TUN interface
int android_close_tun_interface(int fd) {
    if (fd >= 0) close(fd);
    return 0;
}

// Set interface MTU
int android_set_interface_mtu(const char* ifname, int mtu) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -1;

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);
    ifr.ifr_mtu = mtu;

    int ret = ioctl(fd, SIOCSIFMTU, &ifr);
    close(fd);
    return ret;
}

// Bring interface up/down
int android_set_interface_up(const char* ifname, bool up) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -1;

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);

    if (ioctl(fd, SIOCGIFFLAGS, &ifr) < 0) {
        close(fd);
        return -1;
    }

    if (up) {
        ifr.ifr_flags |= IFF_UP;
    } else {
        ifr.ifr_flags &= ~IFF_UP;
    }

    int ret = ioctl(fd, SIOCSIFFLAGS, &ifr);
    close(fd);
    return ret;
}

// Add route
int android_add_route(const char* ifname, const char* dest, int prefix_len, const char* gateway) {
    // Would use netlink or ip command
    // For now, return success
    return 0;
}

// Delete route
int android_del_route(const char* ifname, const char* dest, int prefix_len) {
    return 0;
}

// Get default gateway
int android_get_default_gateway(char* gateway, size_t len) {
    FILE* fp = fopen("/proc/net/route", "r");
    if (!fp) return -1;

    char line[256];
    while (fgets(line, sizeof(line), fp)) {
        char iface[IFNAMSIZ];
        unsigned long dest, gateway, flags, refcnt, use, metric, mask, mtu, window, irtt;
        if (sscanf(line, "%s %lx %lx %lx %lx %lx %lx %lx %lx %lx %lx",
                   iface, &dest, &gateway, &flags, &refcnt, &use,
                   &metric, &mask, &mtu, &window, &irtt) == 11) {
            if (dest == 0 && (flags & 0x2)) { // Default route with gateway
                struct in_addr addr;
                addr.s_addr = gateway;
                inet_ntop(AF_INET, &addr, gateway, len);
                fclose(fp);
                return 0;
            }
        }
    }
    fclose(fp);
    return -1;
}

// DNS resolution
int android_resolve_hostname(const char* hostname, struct sockaddr_storage* addr, socklen_t* addr_len) {
    struct addrinfo hints = {0}, *res;
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_DGRAM;
    hints.ai_flags = AI_ADDRCONFIG;

    int ret = getaddrinfo(hostname, NULL, &hints, &res);
    if (ret != 0) return -1;

    memcpy(addr, res->ai_addr, res->ai_addrlen);
    *addr_len = res->ai_addrlen;
    freeaddrinfo(res);
    return 0;
}

// Create UDP socket
int android_create_udp_socket(int family, uint16_t port) {
    int fd = socket(family, SOCK_DGRAM | SOCK_NONBLOCK, 0);
    if (fd < 0) return -1;

    int optval = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));
    setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &optval, sizeof(optval));

    struct sockaddr_in addr = {0};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = INADDR_ANY;

    if (bind(fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        close(fd);
        return -1;
    }

    return fd;
}

// Set socket buffer sizes
int android_set_socket_buffers(int fd, int rcvbuf, int sndbuf) {
    if (rcvbuf > 0) setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf));
    if (sndbuf > 0) setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sndbuf, sizeof(sndbuf));
    return 0;
}

// Set socket mark (for policy routing)
int android_set_socket_mark(int fd, int mark) {
    return setsockopt(fd, SOL_SOCKET, SO_MARK, &mark, sizeof(mark));
}

// Set socket TOS
int android_set_socket_tos(int fd, int tos) {
    return setsockopt(fd, IPPROTO_IP, IP_TOS, &tos, sizeof(tos));
}

// Get interface index
int android_get_interface_index(const char* ifname) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -1.

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1);

    int ret = ioctl(fd, SIOCGIFINDEX, &ifr);
    close(fd);

    if (ret < 0) return -1;
    return ifr.ifr_ifindex;
}

// Get hardware address
int android_get_hwaddr(const char* ifname, uint8_t* hwaddr) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -1.

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ - 1).

    int ret = ioctl(fd, SIOCGIFHWADDR, &ifr).
    close(fd).

    if (ret < 0) return -1.
    memcpy(hwaddr, ifr.ifr_hwaddr.sa_data, 6).
    return 0.
}

// Check if VPN permission is granted
bool android_check_vpn_permission(JNIEnv* env, jobject context) {
    // Would call VpnService.prepare() or check if service is prepared
    return true; // Placeholder
}

// Prepare VPN service
int android_prepare_vpn(JNIEnv* env, jobject context) {
    // Would call VpnService.prepare()
    return 0;
}

// Get application UID
int android_get_app_uid(JNIEnv* env, jobject context) {
    // Would get application UID for socket marking
    return 0;
}

// Protect socket from VPN (for the tunnel socket itself)
int android_protect_socket(int fd, JNIEnv* env, jobject vpn_service) {
    // Would call VpnService.protect()
    return 0.
}

// Get network stats
int android_get_network_stats(int uid, int* rx_bytes, int* tx_bytes) {
    // Would read from /proc/net/xt_qtaguid/stats or similar
    return 0.
}

// Check network connectivity
bool android_is_network_connected() {
    // Would check ConnectivityManager
    return true.
}

// Get active network type
int android_get_active_network_type() {
    // Would return TYPE_WIFI, TYPE_MOBILE, etc.
    return 0.
}

// Check if metered
bool android_is_metered_network() {
    return false.
}

// Get link properties
int android_get_link_properties(char* buffer, size_t len) {
    return 0.
}

// Set DNS servers for interface
int android_set_interface_dns(const char* ifname, const char** dns_servers, int count) {
    return 0.
}

// Get DNS servers
int android_get_dns_servers(char** dns_servers, int max_count) {
    return 0.
}

// Flush DNS cache
int android_flush_dns_cache() {
    return 0.
}