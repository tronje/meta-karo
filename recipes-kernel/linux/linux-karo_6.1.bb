SUMMARY = "Linux Kernel for Ka-Ro electronics Computer-On-Modules"

FILESEXTRAPATHS:prepend := "${THISDIR}/${BP}/patches:"

require recipes-kernel/linux/linux-karo.inc

LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

SRCBRANCH = "linux-6.1.y"
SRCREV = "316071de521e96bc0f7e928e3d20508891961fa4"
KERNEL_SRC = "git://git.kernel.org/pub/scm/linux/kernel/git/stable/linux.git"

SRC_URI:append = " \
        file://${KBUILD_DEFCONFIG} \
        ${@ "".join(map(lambda f: " file://cfg/" + f, "${KERNEL_FEATURES}".split()))} \
"

SRC_URI:append:stm32mp1 = " \
        file://dts/stm32mp15-karo.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
        file://dts/stm32mp15-qsmp.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
        file://dts/stm32mp15-txmp.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
        file://dts/stm32mp153-karo.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
        file://dts/stm32mp153-qsmp.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
        file://dts/stm32mp153-txmp.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
        file://dts/stm32mp157-karo.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
        file://dts/stm32mp157-qsmp.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
        file://dts/stm32mp157-txmp.dtsi;subdir=git/${KERNEL_OUTPUT_DIR} \
"

KERNEL_LOCALVERSION = "${LINUX_VERSION_EXTENSION}"
KERNEL_IMAGETYPE:stm32mp1 = "uImage"
# KERNEL_IMAGETYPE:stm32mp13 = "Image"

KBUILD_DEFCONFIG = "defconfig"
KBUILD_DEFCONFIG:qsmp-1510 = "qsmp-1510_defconfig"

KERNEL_FEATURES:append = "${@bb.utils.contains('DISTRO_FEATURES',"bluetooth"," bluetooth.cfg","",d)}"
KERNEL_FEATURES:append = "${@bb.utils.contains('DISTRO_FEATURES',"wifi"," wifi.cfg","",d)}"
KERNEL_FEATURES:append = "${@bb.utils.contains('DISTRO_FEATURES',"systemd"," systemd.cfg","",d)}"
KERNEL_FEATURES:append = "${@bb.utils.contains('DISTRO_FEATURES',"ipv6"," ipv6.cfg","",d)}"

KERNEL_FEATURES:append = "${@bb.utils.contains('MACHINE_FEATURES',"extmod"," extmod.cfg","",d)}"

COMPATIBLE_MACHINE:stm32mp1 = "(txmp-.*|qsmp-.*)"
