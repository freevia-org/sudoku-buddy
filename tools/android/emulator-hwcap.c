// Debug emulator workaround only. Apple HVF advertises SVE2 without SVE.
// Preserve the real auxiliary vector except for an impossible SVE2 capability.
#include <dlfcn.h>
#include <sys/auxv.h>
unsigned long getauxval(unsigned long type) {
    unsigned long (*original)(unsigned long) = dlsym(RTLD_NEXT, "getauxval");
    unsigned long value = original(type);
    if (type == AT_HWCAP2 && !(original(AT_HWCAP) & (1UL << 22))) value &= ~(1UL << 1);
    return value;
}
