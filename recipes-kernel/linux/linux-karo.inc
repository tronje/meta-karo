# Copyright (C) 2012, 2015 O.S. Systems Software LTDA.
# Released under the MIT license (see COPYING.MIT for the terms)

LICENSE = "GPL-2.0-only"

inherit kernel
LINUX_VERSION_EXTENSION ?= "-karo"

PROVIDES += "linux"
DEPENDS += "lzop-native bc-native dtc-native"

S = "${WORKDIR}/git"

# We need to pass it as param since kernel might support more then one
# machine, with different entry points
KERNEL_EXTRA_ARGS:stm32mp1 += "LOADADDR=${UBOOT_ENTRYPOINT}"

EXTRA_OEMAKE:append = " V=0"

OVERLAY_INC_FILE = "${SOC_PREFIX}-overlays.inc"
OVERLAY_INC_FILE:rzg2 = "rzg2-overlays.inc"
require conf/machine/include/${OVERLAY_INC_FILE}

#OVERLAY_SRC_FILES ?= ""

SRC_URI = "${KERNEL_SRC};protocol=https;branch=${SRCBRANCH}"

# automatically add all .dts files referenced by ${KERNEL_DEVICETREE} to SRC_URI
SRC_URI:append = "${@"".join(map(lambda f: " file://dts/%s;subdir=git/${KERNEL_OUTPUT_DIR}" % f.replace(".dtb", ".dts"), "${KERNEL_DEVICETREE}".split()))}"
#SRC_URI:append = "${@"".join(map(lambda f: " file://dts/%s;subdir=git/${KERNEL_OUTPUT_DIR}" % f, "${OVERLAY_SRC_FILES}".split()))}"

SRC_URI:append:rzg2 = "${@ "".join(map(lambda f: " file://dts/renesas/%s-%s.dtsi;subdir=git/${KERNEL_OUTPUT_DIR}" % (d.getVar('SOC_PREFIX'), f), d.getVar('DTB_OVERLAY_INCLUDES').split()))}"
SRC_URI:append:stm32mp1 = "${@ "".join(map(lambda f: " file://dts/overlays/%s-%s.dtsi;subdir=git/${KERNEL_OUTPUT_DIR}" % (d.getVar('SOC_PREFIX'), f), d.getVar('DTB_OVERLAY_INCLUDES').split()))}"

KERNEL_DTC_FLAGS += "-@"

def get_overlays(d, vendor="", ext="dtb"):
    ovlist = []
    g = d.getVar('DTB_OVERLAYS_generic') or ""
    o = d.getVar('DTB_OVERLAYS') or ""
    for ov in g.split():
        for fn in ov.split(","):
            fn = " %s%s-%s.%s" % (vendor + "/" if vendor != "" else "", d.getVar('SOC_PREFIX'), fn, ext)
            if fn in ovlist:
                bb.debug(2, "'%s' is already in overlays" % fn)
            else:
                bb.debug(2, "Adding '%s' to overlays" % fn)
                ovlist += [fn]

    for ov in o.split():
        for fn in ov.split(","):
            fn = " %s%s-%s.%s" % (vendor + "/" if vendor != "" else "", d.getVar('SOC_FAMILY'), fn, ext)
            if fn in ovlist:
                bb.debug(2, "'%s' is already in overlays" % fn)
            else:
                bb.debug(2, "Adding '%s' to overlays" % fn)
                ovlist += [fn]

    if len(ovlist) == 0:
        return ""
    bb.debug(2, "ovlist='%s'" % ovlist)
    return " ".join(ovlist)

KERNEL_DEVICETREE:append:rzg2l = "${@ get_overlays(d, "renesas", "dtbo") }"
KERNEL_DEVICETREE:append:stm32mp1 = "${@ get_overlays(d) }"

do_check_config() {
    ifs="$IFS"
    IFS="
"
    applied=$(fgrep -f "${WORKDIR}/${KBUILD_DEFCONFIG}" "${B}/.config" | sort)
    configured=$(cat "${WORKDIR}/${KBUILD_DEFCONFIG}" | sort)
    if [ "$applied" != "$configured" ];then
        bad=$(fgrep -f "${WORKDIR}/${KBUILD_DEFCONFIG}" ${B}/.config | \
                fgrep -vf - "${WORKDIR}/${KBUILD_DEFCONFIG}" | sed 's/^# //;s/[= ].*$//')
        for pat in $bad;do
            override=false
            for f in ${KERNEL_FEATURES};do
                if fgrep -qw "$pat" "${WORKDIR}/cfg/${f}";then
                    bbdebug 1 "'$(fgrep -w "$pat" "${WORKDIR}/${KBUILD_DEFCONFIG}")' from '${KBUILD_DEFCONFIG}' has been overridden by '${f}'"
                    override=true
                    break
                fi
            done
        done
        if ! $override;then
            bbwarn "'$(fgrep -w "$pat" "${WORKDIR}/${KBUILD_DEFCONFIG}")' from '${KBUILD_DEFCONFIG}' has not been accepted by Kconfig"
        fi
    fi
    obsolete="$(fgrep -f "${WORKDIR}/${KBUILD_DEFCONFIG}" "${B}/defconfig" | \
            fgrep -vf - "${WORKDIR}/${KBUILD_DEFCONFIG}")"
    for pat in $obsolete;do
        if fgrep -qw "$pat" "${B}/.config";then
            bbwarn "'$(fgrep -w "$pat" "${WORKDIR}/${KBUILD_DEFCONFIG}")' is obsolete in '${KBUILD_DEFCONFIG}'"
        fi
    done

    for f in ${KERNEL_FEATURES};do
        bbdebug 1 "Checking '$f'"
        applied=$(fgrep -f "${WORKDIR}/cfg/${f}" ${B}/.config | sort)
        configured=$(cat "${WORKDIR}/cfg/${f}" | sort)
        if [ "$applied" != "$configured" ];then
            bad=$(fgrep -f "${WORKDIR}/cfg/${f}" ${B}/.config | \
                    fgrep -vf - "${WORKDIR}/cfg/${f}" | sed 's/^# //;s/[= ].*$//')
            for pat in $bad;do
                bbwarn "'$(fgrep -w "$pat" "${WORKDIR}/cfg/${f}")' from '${f}' has not been accepted by Kconfig"
            done
        fi
        obsolete="$(fgrep -f "${WORKDIR}/cfg/${f}" "${B}/defconfig" | \
                fgrep -vf - "${WORKDIR}/cfg/${f}" | \
                sed 's/^# //;s/[= ].*$//')"
        for pat in $obsolete;do
            cfg=$(fgrep -qw "$pat" "${B}/.config" | sort)
            def=$(fgrep -qw "$pat" "${WORKDIR}/cfg/${f}" | sort)
            if fgrep -qw "$pat" "${B}/.config";then
                if fgrep -qw "$pat" "${WORKDIR}/defconfig";then
                    bbdebug 1 "$(fgrep -w "$pat" "${WORKDIR}/cfg/${f}")' from '${f}' overrides '$(fgrep -w "$pat" "${WORKDIR}/defconfig")' from '${KBUILD_DEFCONFIG}'"
                else
                    bbwarn "$(fgrep -w "$pat" "${WORKDIR}/cfg/${f}")' is obsolete in '${f}'"
                fi
            fi
        done
    done
    IFS="$ifs"
}
addtask do_check_config after do_savedefconfig
do_check_config[nostamp] = "1"

do_compile_dtbs() {
    oe_runmake -C ${B} DTC_FLAGS="${KERNEL_DTC_FLAGS}" ${KERNEL_DEVICETREE}
}
addtask do_compile_dtbs after do_configure before do_check_dtbs

do_configure:prepend() {
    # Add GIT revision to the local version
    head=`git --git-dir=${S}/.git rev-parse --verify --short HEAD 2> /dev/null`
    if ! [ -s "${S}/.scmversion" ] || ! grep -q "$head" ${S}/.scmversion;then
        echo "+g$head" > "${S}/.scmversion"
    fi
    install -v "${WORKDIR}/${KBUILD_DEFCONFIG}" "${B}/.config"

    sed -i '/CONFIG_LOCALVERSION/d' "${B}/.config"
    echo 'CONFIG_LOCALVERSION="${KERNEL_LOCALVERSION}"' >> "${B}/.config"

    for f in ${KERNEL_FEATURES};do
        cat ${WORKDIR}/cfg/${f} >> ${B}/.config
    done
}
addtask do_configure before do_devshell

dtb_ext = "dtb"
dtb_vendordir = ""
dtb_ext:rzg2 = "dtbo"
dtb_vendordir:rzg2 = "renesas"

python do_check_dtbs () {
    import os
    pfx = d.getVar('SOC_PREFIX')
    fam = d.getVar('SOC_FAMILY')
    dtb_ext = d.getVar('dtb_ext')
    dtb_vendordir = d.getVar('dtb_vendordir')

    def get_ovname(name):
        fn = []
        for n in name.split(","):
            if os.path.exists("%s-%s.%s" % (fam, n, dtb_ext)):
                fn.append("%s-%s.%s" % (fam, n, dtb_ext))
            elif os.path.exists("%s-%s.%s" % (pfx, n, dtb_ext)):
                fn.append("%s-%s.%s" % (pfx, n, dtb_ext))
            else:
                bb.fatal("Overlay file '[%s,%s]-%s.%s' not found" % (fam, pfx, n, dtb_ext))
        return fn

    def apply_overlays(infile, outfile, overlays):
        ovlist = []
        for f in overlays.split():
            ovlist += get_ovname(f)
        if len(ovlist) == 0:
            bb.fatal("No files found for overlays %s" % overlays)
            return
        bb.debug(2, "ovlist=%s" % " ".join(ovlist))
        ovfiles = " ".join(map(lambda f: "'%s'" % f, ovlist))
        bb.debug(2, "ovfiles=%s" % ovfiles)
        cmd = ("fdtoverlay -i '%s.dtb' -o '%s.dtb' %s" % (infile, outfile, ovfiles))
        bb.debug(2, "%s" % cmd)
        if os.system("%s" % cmd):
            bb.fatal("Failed to apply overlays %s for baseboard '%s' to '%s.dtb'" %
                     (",".join(ovfiles.split()), baseboard, infile))
            return
        bb.note("FDT overlays %s for '%s' successfully applied to '%s.dtb'" %
            (ovfiles, baseboard, infile))

    here = os.getcwd()
    baseboards = d.getVar('KARO_BASEBOARDS')
    if baseboards == None:
        bb.warn("KARO_BASEBOARDS is not set; cannot process FDT overlays")
        return
    basename = d.getVar('DTB_BASENAME')
    if basename == None:
        bb.warn("DTB_BASENAME is not set; cannot process FDT overlays")
        return
    if dtb_vendordir == "":
        os.chdir(os.path.join(d.getVar('B'), d.getVar('KERNEL_OUTPUT_DIR'), "dts"))
    else:
        os.chdir(os.path.join(d.getVar('B'), d.getVar('KERNEL_OUTPUT_DIR'), "dts", dtb_vendordir))
    for baseboard in baseboards.split():
        bb.debug(2, "creating %s-%s.dtb from %s.dtb" % (basename, baseboard, basename))
        outfile = "%s-%s" % (basename, baseboard)
        overlays = " ".join(map(lambda f: f, d.getVarFlag('KARO_DTB_OVERLAYS', baseboard, True).split()))
        bb.note("overlays_%s=%s" % (baseboard, overlays))
        if overlays == None or len(overlays.split()) == 0:
            bb.fatal("%s: No overlays specified for %s" % (d.getVar('MACHINE'), baseboard))
        bb.debug(2, "overlays for %s-%s='%s'" %
                 (basename, baseboard, "','".join(overlays.split())))
        apply_overlays(basename, outfile, overlays)
    os.chdir(here)
}
addtask do_check_dtbs after do_compile_dtbs before do_compile
