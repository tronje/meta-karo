echo "---------------  HELLO FROM SCRIPT !!!!!!!!!!!!! -----------------"
 
test -n "${BOOT_ORDER}" || setenv BOOT_ORDER "A B"
test -n "${BOOT_A_LEFT}" || setenv BOOT_A_LEFT 3
test -n "${BOOT_B_LEFT}" || setenv BOOT_B_LEFT 3

setenv bootargs_mmc
for BOOT_SLOT in "${BOOT_ORDER}"; do
  if test "x${bootargs_mmc}" != "x"; then
    # skip remaining slots
  elif test "x${BOOT_SLOT}" = "xA"; then
    if test 0x${BOOT_A_LEFT} -gt 0; then
      echo "Found valid slot A, ${BOOT_A_LEFT} attempts remaining"
      setexpr BOOT_A_LEFT ${BOOT_A_LEFT} - 1
      #setenv load_kernel "nand read ${kernel_loadaddr} ${kernel_a_nandoffset} ${kernel_size}"
      #setenv bootargs "${default_bootargs} root=/dev/mmcblk0p1 rauc.slot=A"
      setenv bootargs_mmc 'run default_bootargs;setenv bootargs ${bootargs} root=PARTUUID=${uuid_rootfs} rauc.slot=A rootwait ${append_bootargs} ${dyndbg}'
    fi
  elif test "x${BOOT_SLOT}" = "xB"; then
    if test 0x${BOOT_B_LEFT} -gt 0; then
      echo "Found valid slot B, ${BOOT_B_LEFT} attempts remaining"
      setexpr BOOT_B_LEFT ${BOOT_B_LEFT} - 1
      #setenv load_kernel "nand read ${kernel_loadaddr} ${kernel_b_nandoffset} ${kernel_size}"
      #setenv bootargs "${default_bootargs} root=/dev/mmcblk0p2 rauc.slot=B"
      setenv bootargs_mmc 'run default_bootargs;setenv bootargs ${bootargs} root=PARTUUID=${uuid_rootfsB} rauc.slot=B rootwait ${append_bootargs} ${dyndbg}'
    fi
  fi
done

if test -n "${bootargs_mmc}"; then
  saveenv
else
  echo "No valid slot found, resetting tries to 3"
  setenv BOOT_A_LEFT 3
  setenv BOOT_B_LEFT 3
  saveenv
  reset
fi

#echo "Loading kernel"
#run load_kernel_${boot_mode}
#echo " Starting kernel"
#bootm ${loadaddr_kernel}