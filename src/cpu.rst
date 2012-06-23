
JVMのCPU依存部分について
###############################################################################

MacroAssembler
*******************************************************************************

cpu依存部分には、MacroAssemblerで色々記述されている。

assembler_x86.cpp

UseCompressedOopsの処理も、大体ここに入っている。

string_indexof
===============================================================================

  int_cnt2は8より小さい。generalなアルゴリズムになっている。


string_indexofC8
===============================================================================

  OpenJDK7から追加

  size > 8 の前提条件下で動作する。条件が畳み込んであり高速

概要 ::

  int string_indexofC8(short* str1, int str1_len, short* str2, int str2_len)

  string_indexofC8
    str1
    str2
    cnt1
    cnt2
    int_cnt2 <-- >8
    result

    pcmpestri
    input
      xmm substring
      rax substring length
      mem scanned string
      rdx string length
      0xd  mode指定
           1100 部分文字列比較
             01 符号なしデータ short比較
    output
      rcx matched index in string


string_compare
===============================================================================

  if supports_cmov()

  if UseSSE42Intrinsics
    pcmpestri


概要 ::

  void
  string_compare(short* str1, short* str2,
    int cnt1, int cnt2, int& result, void* vec1) {
  }
  
  // pcmpestri
  //   inputs:
  //     vec1- substring
  //     rax - negative string length (elements count)
  //     mem - scaned string
  //     rdx - string length (elements count)
  //     pcmpmask - cmp mode: 11000 (string compare with negated result)
  //               + 00 (unsigned bytes) or  + 01 (unsigned shorts)
  //   outputs:
  //     rcx - first mismatched element index

カーネルループ ::

  COMPARE_WIDE_VECTORS:
    movdqu vec1, (str1, result, scale)
    pcmpestri vec1, (str2, result, scale), pcmpmask
    jccb VECTOR_NOT_EQUAL // CF=(IntRes2!=0),  CF==1(IntRes2==0), goto VECTOR_NOT_EQ CF==0(IntRes2!=0) goto loop
    addptr result, stride
    subptr cnt2, stride
    jccb COMPARE_WIDE_VECTORS


char_array_equals
===============================================================================

  if UseSSE42Intrinsics
    movdqu vec1
    movdqu vec2
    pxor vec1 vec2
    ptest vec1


概要 ::
  
  // Compare char[] arrays aligned to 4 bytes or substrings.
  void MacroAssembler::char_arrays_equals(bool is_array_equ, Register ary1, Register ary2,
  Register limit, Register result, Register chr,
  XMMRegister vec1, XMMRegister vec2) {
  
  // Compare 16-byte vectors
  andl(result, 0x0000000e);  //   tail count (in bytes)
  andl(limit, 0xfffffff0);   // vector count (in bytes)
  jccb(Assembler::zero, COMPARE_TAIL);
  
  lea(ary1, Address(ary1, limit, Address::times_1));
  lea(ary2, Address(ary2, limit, Address::times_1));
  negptr(limit);
  
  bind(COMPARE_WIDE_VECTORS);
  movdqu(vec1, Address(ary1, limit, Address::times_1));
  movdqu(vec2, Address(ary2, limit, Address::times_1));
  pxor(vec1, vec2);
  
  ptest(vec1, vec1);
  jccb(Assembler::notZero, FALSE_LABEL);
  addptr(limit, 16);
  jcc(Assembler::notZero, COMPARE_WIDE_VECTORS);
  
  testl(result, result);
  jccb(Assembler::zero, TRUE_LABEL);
  
  movdqu(vec1, Address(ary1, result, Address::times_1, -16));
  movdqu(vec2, Address(ary2, result, Address::times_1, -16));
  pxor(vec1, vec2);
  
  ptest(vec1, vec1);
  jccb(Assembler::notZero, FALSE_LABEL);
  jmpb(TRUE_LABEL);
  
  bind(COMPARE_TAIL); // limit is zero
  movl(limit, result);
  // Fallthru to tail compare
  


generate_fill
===============================================================================

  配列のfillを高速に行うMacroなのかな。

biased_locking_enter
===============================================================================

  cmpxchg でロックフリーなアルゴリズムだったような。。

g1gc
===============================================================================


JavaのAPIが内部でmacroに切り替わるタイミング
===============================================================================

stack trace ::

  #8  0x005a75fc in ParseGenerator::generate (this=0x8087e38, jvms=0x8083768)
      at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/callGenerator.cpp:87
  87    Parse parser(jvms, method(), _expected_uses);
  (gdb) 
  #7  0x00a3b117 in Parse::Parse (this=0x7d31157c, caller=0x8083768, parse_method=0x81439b0, expected_uses=10000)
      at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/parse1.cpp:589
  589   do_all_blocks();
  (gdb) 
  #6  0x00a3b5b1 in Parse::do_all_blocks (this=0x7d31157c)
      at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/parse1.cpp:680
  680       do_one_block();
  (gdb) 
  #5  0x00a3e670 in Parse::do_one_block (this=0x7d31157c)
      at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/parse1.cpp:1405
  1405      do_one_bytecode();
  (gdb) 
  #4  0x00a4c142 in Parse::do_one_bytecode() () at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/parse2.cpp:2228
  2228      do_call();
  (gdb) 
  #3  0x00723e1f in Parse::do_call (this=0x7d31157c) at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/doCall.cpp:483
  483   if ((new_jvms = cg->generate(jvms)) == NULL) {
  (gdb) 
  #2  0x00927009 in LibraryIntrinsic::generate (this=0x8087f98, jvms=0x817bed8)
      at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/library_call.cpp:405
  405   if (kit.try_to_inline()) {
  (gdb) 
  #1  0x009275af in LibraryCallKit::try_to_inline (this=0x7d310d9c)
      at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/library_call.cpp:480
  480     return inline_string_compareTo();
  (gdb) 
  #0  LibraryCallKit::inline_string_compareTo (this=0x7d310d9c)
      at /home/elise/language/java/openjdk7/hotspot/src/share/vm/opto/library_call.cpp:912
  912   Node *receiver = pop();
  



OpenJDK8から
================================================================================

OpenJDK8からAVXをサポートし始めたが、

AVXを使った特別なMacroは未定義


fast_pow, fast_expが新規追加されている

FPUユニットを使って高速にlogを計算して、powとexpを計算するぽい

基本的には、fyl2xやfldl2eを使う。


================================================================================
--------------------------------------------------------------------------------
