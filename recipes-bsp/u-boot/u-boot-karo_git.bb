require recipes-bsp/u-boot/u-boot.inc

OVERLAY_INC_FILE = "${SOC_PREFIX}-overlays.inc"
OVERLAY_INC_FILE:rzg2 = "rzg2-overlays.inc"
require conf/machine/include/${OVERLAY_INC_FILE}

DESCRIPTION = "U-Boot for Ka-Ro electronics TX Computer-On-Modules."
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=2ca5f2c35c8cc335f0a19756634782f1"
LIC_FILES_CHKSUM:rzg2l = "file://Licenses/README;md5=5a7450c57ffe5ae63fd732446b988025"

PROVIDES += "u-boot"

DEPENDS:append = " \
    bc-native \
    bison-native \
    xxd-native \
    python3-setuptools-native \
    fiptool-native \
    ${@bb.utils.contains('MACHINE_FEATURES', 'optee', 'optee-os', '', d)} \
"

FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}/env:${THISDIR}/${PN}/defconfigs:"

SRC_URI = "${UBOOT_SRC};branch=${SRCBRANCH}"
SRCBRANCH = "${@ d.getVar('UBOOT_BRANCH') if d.getVar('UBOOT_BRANCH') else \
            d.getVar('UBOOT_BRANCH_DEFAULT')}"
SRCREV = "${@ d.getVar('UBOOT_REV') if d.getVar('UBOOT_REV') else \
            d.getVar('UBOOT_REV_DEFAULT')}"

UBOOT_SRC ?= "git://github.com/karo-electronics/karo-tx-uboot.git;protocol=https"

UBOOT_BRANCH_DEFAULT:stm32mp1 = "karo-stm32mp1-v2022.10"
UBOOT_REV_DEFAULT:stm32mp1 = "33201673fbe502df252bbd2826ae2386d98a8677"

UBOOT_BRANCH_DEFAULT:rzg2 = "karo-txrz"
UBOOT_REV_DEFAULT:rzg2 = "66fed3c1ba38bf7229e10bfd9923b89bd7e98064"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

EXTRA_OEMAKE:append = " V=0"

# append git hash to u-boot name
SCMVERSION ??= "y"
LOCALVERSION ??= "-karo"

UBOOT_BOARD_DIR:stm32mp1 = "board/karo/stm32mp1"
UBOOT_BOARD_DIR:rzg2 = "board/karo/txrz"

UBOOT_LOCALVERSION = "${LOCALVERSION}"
UBOOT_INITIAL_ENV = "${@ bb.utils.contains('IMAGE_INSTALL', 'u-boot-fw-utils', "u-boot-initial-env", "", d)}"
UBOOT_DEVICE_TREE ?= "${@ "${DTB_BASENAME}-${KARO_BASEBOARD}" if "${KARO_BASEBOARD}" != "" else "${DTB_BASENAME}"}"
UBOOT_FEATURES:append = "${@bb.utils.contains('DISTRO_FEATURES', 'rauc', ' rauc', '', d)}"

UBOOT_ENV_FILE ?= "${@ "%s%s%s" % (d.getVar('MACHINE'), \
                       "-" + d.getVar('KARO_BASEBOARD') \
                       if d.getVar('KARO_BASEBOARD') != "" else "", \
                       "-" + "rauc" \
                       if 'rauc' in d.getVar('DISTRO_FEATURES').split() else "")}"



SRC_URI:append = "${@ " file://%s.env;subdir=git/%s" % \
                      (d.getVar('UBOOT_ENV_FILE'), d.getVar('UBOOT_BOARD_DIR')) \
                      if d.getVar('UBOOT_ENV_FILE') != "" else ""} \
"

SRC_URI:append = " \
    file://dts/${UBOOT_DEVICE_TREE}.dts;subdir=git/arch/arm \
    file://dts/${UBOOT_DEVICE_TREE}-u-boot.dtsi;subdir=git/arch/arm \
"

SRC_URI:append = " file://u-boot-cfg.${SOC_PREFIX}"
SRC_URI:append = " file://u-boot-cfg.${SOC_FAMILY}"
SRC_URI:append = " file://u-boot-cfg.${MACHINE}"
SRC_URI:append = "${@ "".join(map(lambda f: " file://u-boot-cfg.%s" % f, d.getVar('UBOOT_CONFIG').split()))}"
SRC_URI:append = "${@ "".join(map(lambda f: " file://cfg/%s.cfg" % f, d.getVar('UBOOT_FEATURES').split()))}"

SRC_URI:append = "${@ bb.utils.contains('IMAGE_INSTALL', 'u-boot-fw-utils', " file://fw_env.config", "", d)}"

UBOOT_FEATURES:append = "${@ " " + d.getVar('KARO_BASEBOARD') if d.getVar('KARO_BASEBOARD') in "qsbase1 qsbase4".split() else ""}"

do_deploy[depends] += "virtual/trusted-firmware-a:do_deploy"
do_deploy[depends] += "${@bb.utils.contains('MACHINE_FEATURES', 'optee', 'optee-os:do_deploy', '', d)}"

do_configure:prepend:stm32mp1() {
    if [ -z "${UBOOT_CONFIG}" ]; then
        bbfatal "Wrong u-boot-karo configuration: please make sure to use UBOOT_CONFIG through BOOTSCHEME_LABELS config"
    fi
}

do_configure() {
    if [ -n "${UBOOT_CONFIG}" ];then
        i=0
        for config in ${UBOOT_MACHINE};do
            i=$(expr $i + 1)
            j=0
            for type in ${UBOOT_CONFIG};do
                j=$(expr $j + 1)
                [ $j -lt $i ] && continue
                c="$(echo "$config" | sed s/_config/_defconfig/)"
                cp -v "${WORKDIR}/u-boot-cfg.${SOC_PREFIX}" "${S}/configs/${c}"
                if [ "${SOC_FAMILY}" != "${SOC_PREFIX}" ];then
                    bbnote "Appending 'u-boot-cfg.${SOC_FAMILY}' to 'configs/${c}'"
                    cat "${WORKDIR}/u-boot-cfg.${SOC_FAMILY}" >> "${S}/configs/${c}"
                fi
                bbnote "Appending 'u-boot-cfg.${MACHINE}' to 'configs/${c}'"
                cat "${WORKDIR}/u-boot-cfg.${MACHINE}" >> "${S}/configs/${c}"
                if [ -s "${WORKDIR}/u-boot-cfg.${type}" ];then
                    bbnote "Appending '$type' specific config to '${S}/configs/${c}'"
                    cat "${WORKDIR}/u-boot-cfg.${type}" >> "${S}/configs/${c}"
                fi
                for feature in ${UBOOT_FEATURES};do
                    bbnote "Appending '$feature' specific config to '${S}/configs/${c}'"
                    cat "${WORKDIR}/cfg/${feature}.cfg" >> "${S}/configs/${c}"
                done
                oe_runmake -C ${S} O=${B}/${config} ${c}
                break
            done
            unset j
        done
        unset i
    else
        c="${MACHINE}_defconfig"
        bbnote "Copying 'u-boot-cfg.${SOC_PREFIX}' to 'configs/${c}'"
        cp "${WORKDIR}/u-boot-cfg.${SOC_PREFIX}" "${S}/configs/${c}"
        if [ "${SOC_FAMILY}" != "${SOC_PREFIX}" ];then
            bbnote "Appending 'u-boot-cfg.${SOC_FAMILY}' to 'configs/${c}'"
            cat "${WORKDIR}/u-boot-cfg.${SOC_FAMILY}" >> "${S}/configs/${c}"
        fi
        if [ "${SOC_FAMILY}" != "${SOC_PREFIX}" ];then
            bbnote "Appending 'u-boot-cfg.${SOC_FAMILY}' to 'configs/${c}'"
            cat "${WORKDIR}/u-boot-cfg.${SOC_FAMILY}" >> "${S}/configs/${c}"
        fi
        bbnote "Appending 'u-boot-cfg.${MACHINE}' to 'configs/${c}'"
        cat "${WORKDIR}/u-boot-cfg.${MACHINE}" >> "${S}/configs/${c}"
        oe_runmake -C ${S} O=${B} ${MACHINE}_defconfig
    fi

    tmpfile="`mktemp cfg-XXXXXX.tmp`"
    if [ -z "$tmpfile" ];then
        bbfatal "Failed to create tmpfile"
    fi
    bbnote "UBOOT_DEVICE_TREE='${UBOOT_DEVICE_TREE}'"
    cat <<EOF >> "$tmpfile"
CONFIG_DEFAULT_DEVICE_TREE="${UBOOT_DEVICE_TREE}"
EOF
    grep -q "${UBOOT_DEVICE_TREE}\.dtb" ${S}/arch/arm/dts/Makefile || \
            sed -i '/^targets /i\
dtb-y += ${UBOOT_DEVICE_TREE}.dtb\
' ${S}/arch/arm/dts/Makefile

    bbnote "UBOOT_ENV_FILE='${UBOOT_ENV_FILE}'"
    if [ -n "${UBOOT_ENV_FILE}" ];then
        cat <<EOF >> "$tmpfile"
CONFIG_USE_DEFAULT_ENV_FILE=y
CONFIG_DEFAULT_ENV_FILE="board/\$(VENDOR)/\$(BOARD)/${UBOOT_ENV_FILE}.env"
EOF
    else
        echo "# CONFIG_USE_DEFAULT_ENV_FILE is not set" >> "$tmpfile"
    fi

    if [ -n "${UBOOT_CONFIG}" ];then
        for config in ${UBOOT_MACHINE};do
            c="$(echo "$config" | sed s/_config/_defconfig/)"
            merge_config.sh -m -r -O "${c}" "${c}/.config" "$tmpfile"
            oe_runmake -C ${c} olddefconfig
            if grep '^CONFIG_.*=$' ${c}/.config;then
                bbfatal "defconfig is incomplete"
            fi
        done
    else
        merge_config.sh -m -r -O "${B}" "${B}/.config" "$tmpfile"
        oe_runmake -C "${B}" olddefconfig
        if grep '^CONFIG_.*=$' ${B}/.config;then
            bbfatal "defconfig is incomplete"
        fi
    fi
    rm -vf "$tmpfile"
}

do_savedefconfig() {
    if [ -n "${UBOOT_CONFIG}" ];then
        for config in ${UBOOT_MACHINE}; do
            bbplain "Saving ${config} defconfig to:\n${B}/${config}/defconfig"
            oe_runmake -C ${B}/${config} savedefconfig
        done
    else
        bbplain "Saving ${config} defconfig to:\n${B}/defconfig"
        oe_runmake -C ${B} savedefconfig
    fi
}
do_savedefconfig[nostamp] = "1"
addtask savedefconfig after do_configure

check_cnf() {
    local src="$1"
    local cfg="$2"
    fgrep -f "$src" "${cfg}/.config" || true
    local applied=$(fgrep -f "$src" "${cfg}/.config" | wc -l)
    local configured=$(cat "$src" | wc -l)
    if [ $applied != $configured ];then
        bbwarn "The following items of '$(basename "$src")' have not been accepted by Kconfig"
        bbwarn ">>>$(fgrep -f "$src" "${cfg}/.config" | fgrep -vf - "$src")<<<"
        local p="$(fgrep -f "$src" "${cfg}/.config" | fgrep -vf - "$src" | \
                sed 's/^# //;s/[= ].*$//')"

        local pat
        for pat in $p;do
            if grep -q "$pat" "${cfg}/.config";then
                bbwarn "Actual Kconfig value of '$pat' is: '$(grep "$pat" "${cfg}/.config")'"
            else
                bbwarn "'$pat' is not present in '$(basename "$cfg")/.config"
            fi
        done
        applied="$(fgrep -f "$src" "${cfg}/defconfig" | wc -l)"
        if [ $applied != $configured ];then
            p="$(fgrep -f "$src" "${cfg}/defconfig" | fgrep -vf - "$src" | \
                    sed 's/^# //;s/[= ].*$//')"
            for pat in $p;do
                bbwarn "'$(fgrep "$pat" "$src")' is obsolete in '$(basename "$src")'"
            done
        fi
    fi
}

do_check_config() {
    bbnote "Checking defconfig consistency"
    if [ -n "${UBOOT_CONFIG}" ];then
        i=0
        for config in ${UBOOT_MACHINE};do
            i=$(expr $i + 1)
            c="${B}/${config}"
            cp -v "${WORKDIR}/u-boot-cfg.${SOC_PREFIX}" "${c}/.config"
            if [ "${SOC_FAMILY}" != "${SOC_PREFIX}" ];then
                merge_config.sh -m -r -O "${c}" "${c}/.config" "${WORKDIR}/u-boot-cfg.${SOC_FAMILY}"
            fi
            merge_config.sh -m -r -O "${c}" "${c}/.config" "${WORKDIR}/u-boot-cfg.${MACHINE}"
            j=0
            for type in ${UBOOT_CONFIG};do
                j=$(expr $j + 1)
                [ $j = $i ] || continue
                if [ -s "${WORKDIR}/u-boot-cfg.${type}" ];then
                    bbnote "Appending '$type' specific config to '$(basename "${c}")/.config'"
                    merge_config.sh -m -r -O "${c}" "${c}/.config" "${WORKDIR}/u-boot-cfg.${type}"
                fi
                break
            done
            oe_runmake -C ${c} olddefconfig
            check_cnf "${WORKDIR}/u-boot-cfg.${SOC_PREFIX}" "${c}"
            if [ "${SOC_FAMILY}" != "${SOC_PREFIX}" ];then
                check_cnf "${WORKDIR}/u-boot-cfg.${SOC_FAMILY}" "${c}"
            fi
            check_cnf "${WORKDIR}/u-boot-cfg.${MACHINE}" "${c}"
            check_cnf "${WORKDIR}/u-boot-cfg.${type}" "${c}"
            for feature in ${UBOOT_FEATURES} ${MACHINE_FEATURES};do
                [ -s "${WORKDIR}/cfg/${feature}.cfg" ] || continue
                bbnote "Appending '$feature' specific config to '$(basename "${c}")/.config'"
                merge_config.sh -m -r -O "${c}" "${c}/.config" "${WORKDIR}/cfg/${feature}.cfg"
                oe_runmake -C ${c} olddefconfig
                check_cnf "${WORKDIR}/cfg/${feature}.cfg" "${c}"
            done
            check_cnf "${WORKDIR}/u-boot-cfg.${SOC_PREFIX}" "${c}"
            if [ "${SOC_FAMILY}" != "${SOC_PREFIX}" ];then
                check_cnf "${WORKDIR}/u-boot-cfg.${SOC_FAMILY}" "${c}"
            fi
            check_cnf "${WORKDIR}/u-boot-cfg.${MACHINE}" "${c}"

            # restore the original config
            cp -v "${c}/defconfig" "${c}/.config"
            oe_runmake -C "${c}" olddefconfig
        done
    else
        cp -v "${WORKDIR}/u-boot-cfg.${SOC_PREFIX}" "${B}/.config"
        if [ "${SOC_FAMILY}" != "${SOC_PREFIX}" ];then
            merge_config.sh -m -r -O "${B}" "${B}/.config" "${WORKDIR}/u-boot-cfg.${SOC_FAMILY}"
            oe_runmake -C ${B} olddefconfig
        fi
        merge_config.sh -m -r -O "${B}" "${B}/.config" "${WORKDIR}/u-boot-cfg.${MACHINE}"
        oe_runmake -C ${B} olddefconfig
        check_cnf "${WORKDIR}/u-boot-cfg.${SOC_PREFIX}" "${B}"
        if [ "${SOC_FAMILY}" != "${SOC_PREFIX}" ];then
            check_cnf "${WORKDIR}/u-boot-cfg.${SOC_FAMILY}" "${B}"
        fi
        check_cnf "${WORKDIR}/u-boot-cfg.${MACHINE}" "${B}"

        # restore the original config
        cp -v "${B}/defconfig" "${B}/.config"
        oe_runmake -C "${B}" olddefconfig
    fi
}
addtask do_check_config after do_savedefconfig
do_check_config[nostamp] = "1"

do_compile:prepend() {
    if [ "${SCMVERSION}" = "y" ]; then
        # Add GIT revision to the local version
        head="`cd ${S} ; git rev-parse --verify --short HEAD 2> /dev/null`"
        printf "%s+g%s" "${UBOOT_LOCALVERSION}" "$head" > ${S}/.scmversion
        printf "%s+g%s" "${UBOOT_LOCALVERSION}" "$head" > ${B}/.scmversion
    else
        printf "%s" "${UBOOT_LOCALVERSION}" > ${S}/.scmversion
        printf "%s" "${UBOOT_LOCALVERSION}" > ${B}/.scmversion
    fi
}

# -----------------------------------------------------------------------------
# Append deploy to handle specific device tree binary deployement
#
def get_tfa_configs(d):
    cfg = ()
    for type in d.getVar('UBOOT_CONFIG').split():
        tfa_cfg = d.getVarFlag('TF_A_CONFIG', type, True)
        if tfa_cfg == None:
            bb.debug(2, "TF_A_CONFIG[%s] is not defined" % type)
            tfa_cfg = type
        bb.debug(2, "TF_A_CONFIG[%s]='%s'" % (type, tfa_cfg))
        cfg += (tfa_cfg,)
    return " ".join(cfg)

TF_A_CONFIGS = "${@ get_tfa_configs(d)}"

do_install () {
    if [ -n "${UBOOT_INITIAL_ENV}" ];then
        for config in ${UBOOT_MACHINE};do
            for type in ${UBOOT_CONFIG};do
                install -D -m 644 ${B}/${config}/u-boot-initial-env-${type} ${D}/${sysconfdir}/${UBOOT_INITIAL_ENV}
                break
            done
            break
        done
    fi
}

do_deploy () {
    # Create fip images
    i=0
    for config in ${UBOOT_MACHINE}; do
        i=$(expr $i + 1)
        j=0
        for type in ${UBOOT_CONFIG}; do
            j=$(expr $j + 1)
            [ $j -lt $i ] && continue
            k=0
            for cfg in ${TF_A_CONFIGS}; do
                k=$(expr $k + 1)
                [ $k -lt $j ] && continue
                for dt in ${TF_A_DEVICETREE}; do
                    fiptool create \
                        --fw-config ${DEPLOY_DIR_IMAGE}/${FIPTOOL_DIR}/${cfg}/${dt}-fw-config.dtb \
                        --hw-config ${B}/${config}/u-boot.dtb \
                        --nt-fw ${B}/${config}/u-boot-nodtb.bin \
                        --tos-fw ${DEPLOY_DIR_IMAGE}/${FIPTOOL_DIR}/tee-header_v2.bin \
                        --tos-fw-extra1 ${DEPLOY_DIR_IMAGE}/${FIPTOOL_DIR}/tee-pager_v2.bin \
                        --tos-fw-extra2 ${DEPLOY_DIR_IMAGE}/${FIPTOOL_DIR}/tee-pageable_v2.bin \
                        ${DEPLOYDIR}/fip-${dt}-${type}.bin
                done
                break
            done
            break
        done
    done
}

do_deploy:append:stm32mp1 () {
    # create bash script to start 'fastboot' on the target via dfu-util
    cat <<EOF > ${DEPLOYDIR}/fastboot.cmd
fastboot 0
EOF
    for config in ${UBOOT_MACHINE};do
        ${B}/${config}/tools/mkimage -C none -A arm -T script -d ${DEPLOYDIR}/fastboot.cmd ${DEPLOYDIR}/fastboot.img
        break
    done
}

do_deploy:rzg2l() {
    # Create fip.bin
    install -v -d "${DEPLOYDIR}/${FIPTOOL_DIR}"
    if [ -n "${UBOOT_CONFIG}" ];then
        i=0
        for config in ${UBOOT_MACHINE};do
            i=$(expr $i + 1)
            j=0
            for type in ${UBOOT_CONFIG};do
                j=$(expr $j + 1)
                [ $j -lt $i ] && continue
                fiptool create --align 16 --soc-fw \
                    "${DEPLOY_DIR_IMAGE}/${FIPTOOL_DIR}/bl31-${MACHINE}.bin" \
                    --nt-fw "${B}/${config}/u-boot-${type}.bin" "${DEPLOYDIR}/${FIPTOOL_DIR}/fip-${MACHINE}-${type}.bin"
                if [ $i = 1 ];then
                    ln -s ${FIPTOOL_DIR}/fip-${MACHINE}-${type}.bin "${DEPLOYDIR}/fip-${MACHINE}.bin"
                fi
                break
            done
        done
    else
        fiptool create --align 16 --soc-fw "${DEPLOY_DIR_IMAGE}/${FIPTOOL_DIR}/bl31-${MACHINE}.bin" \
            --nt-fw "${B}/u-boot.bin" "${DEPLOYDIR}/${FIPTOOL_DIR}/fip-${MACHINE}.bin"
    fi
}

python do_env_overlays () {
    import os
    import shutil

    if d.getVar('KARO_BASEBOARDS') == None:
        bb.warn("KARO_BASEBOARDS is undefined")
        return 1
    if d.getVar('UBOOT_ENV_FILE') == None:
        bb.warn("UBOOT_ENV_FILE is undefined")
        return 1

    overlays = []
    for baseboard in d.getVar('KARO_BASEBOARDS').split():
        ovlist = d.getVarFlag('KARO_DTB_OVERLAYS', baseboard, True)
        if ovlist == None:
            bb.note("No overlays defined for '%s' on baseboard '%s'" % (d.getVar('MACHINE'), baseboard))
            continue

        ovl = "overlays_%s=" % baseboard
        dlm = ""
        for ov in ovlist.split():
            ovf = ov.split(",")
            # omit alternatively used overlays
            if len(ovf) == 1:
                ovl += dlm + ovf[0]
                dlm = " "
        overlays += [ovl]

    src_file = "%s/%s/%s.env" % (d.getVar('S'), d.getVar('UBOOT_BOARD_DIR'), d.getVar('UBOOT_ENV_FILE'))
    if d.getVar('UBOOT_CONFIG') != None:
        configs = d.getVar('UBOOT_MACHINE').split()
    else:
        configs = (d.getVar('MACHINE'))

    for config in configs:
        dst_dir = "%s/%s/%s" % (d.getVar('B'), config, d.getVar('UBOOT_BOARD_DIR'))
        bb.utils.mkdirhier(dst_dir)
        env_file = os.path.join(dst_dir, os.path.basename(src_file))
        bb.note("Copying %s to %s" % (src_file, env_file))
        shutil.copyfile(src_file, env_file)
        f = open(env_file, 'a')
        for ov in overlays:
            bb.note("Adding '%s' to '%s'" % (ov, env_file))
            f.write("%s\n" % ov)
        f.write("soc_prefix=%s\n" % (d.getVar('SOC_PREFIX') or ""))
        f.write("soc_family=%s\n" % (d.getVar('SOC_FAMILY') or ""))
        f.close()
}
addtask do_env_overlays before do_compile after do_configure
do_env_overlays[vardeps] += "KARO_BASEBOARDS KARO_DTB_OVERLAYS"

# ---------------------------------------------------------------------
# Avoid QA Issue: No GNU_HASH in the elf binary
# ---------------------------------------------------------------------
# Avoid QA Issue: ELF binary has relocations in .text
# (uboot no need -fPIC option : remove check)

PACKAGE_ARCH = "${MACHINE_ARCH}"

COMPATIBLE_MACHINE:rzg2 = "(txrz-.*|qsrz-.*)"
COMPATIBLE_MACHINE:stm32mp1 = "(txmp-.*|qsmp-.*)"

