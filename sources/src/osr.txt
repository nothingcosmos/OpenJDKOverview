
On-Stack Replacement
###############################################################################

OSR
*******************************************************************************

OSRは、ループ実行した際に、ループを内包したmethodをJITコンパイルして、実行途中からJITコンパイルしたコードに遷移する。

OSRでJITコンパイルした場合、methodの入り口に、OSR用の退避コードが挿入される。

OSRは、実現するだけであれば、非常に簡単であり、

OSREntryのframe pointerとthisポインタの位置を保存しておくだけでよいはず。

特定のレジスタに乗せておくというルール制御でも可能だろう。



トレードオフとなるところは、上記重要なアドレス以外を

いかに保存しておくかだと思う。OSRで切り替わるコードが遅くてもよいのであれば、

チェックポイントにおいて、変数はレジスタに退避しておいてよいはず。

でも性能を考えると、レジスタはフルに使いたい。この辺がトレードオフ。

レジスタをフルに使いたいなら、OSRの入れ替え対象のレジスタの意味を

フルで使いたいだけ覚えておき、退避する必要がある。


おそらくだが、ループ中にOSRのEntryを決め打ちで１ヶ所だけ作成し、

そのポイントでだけOSRを行うはず。

OSRのEntryを作成する場所の詳細はまだ不明。

たぶんループのheaderか、backedgeのどちらかのはず。

osrでJITコンパイルされたコードは、もし後で何回も呼ばれるようであれば、

再度JITコンパイルされる。再度JITコンパイルされた場合、OSR用のEntryは当然挿入されない。

OSRの参考資料
===============================================================================

詳細は"コンパイラとバーチャルマシン"っていう書籍が図入りで説明している

comment ::

  // This code is dependent on the memory layout of the interpreter local
  // array and the monitors. On all of our platforms the layout is identical
  // so this code is shared. If some platform lays the their arrays out
  // differently then this code could move to platform specific code or
  // the code here could be modified to copy items one at a time using
  // frame accessor methods and be platform independent.


OSRの調査
===============================================================================

  frame fr = thread->last_frame()

  NEW_C_HEAP_ARRAY()

  Copy::disjoint_words()


OSRの入り口
-------------------------------------------------------------------------------

  OnStackReplacementは、runtime/sharedRuntime.cpp::SharedRuntime::OSR_migration_begin()

OSRの出口
-------------------------------------------------------------------------------

OSR付きでJITコンパイルされたメソッドの入り口に、migration_end()を呼び出す

OSR_migration_end ::

  008   B1: #     B14 B2 <- BLOCK HEAD IS JUNK   Freq: 1
  008     # stack bang
          PUSHL  EBP
          SUB    ESP,40   # Create frame
  016     MOV    ESI,ECX
  018     MOV    EBP,[ECX]        # int
  01a     MOV    EBX,[ECX + #4]
  01d     MOV    EAX.lo,[ESI + #8]        # long
          MOV    EAX.hi,[ESI + #8]+4
  023     MOV    [ESP + #8],EAX
          MOV    [ESP + #12],EDX
  02b     MOV    [ESP + #0],ECX
  02e     CALL_LEAF,runtime  OSR_migration_end
          No JVM State Info



On-Stack Replacementを呼び出す前のインタプリタのコード ::

  #define DO_BACKEDGE_CHECKS(skip, branch_pc)                                                         \
  if ((skip) <= 0) {                                                                              \
  if (UseLoopCounter) {                                                                         \
    bool do_OSR = UseOnStackReplacement;                                                        \
    BACKEDGE_COUNT->increment();                                                                \
    if (do_OSR) do_OSR = BACKEDGE_COUNT->reached_InvocationLimit();                             \
    if (do_OSR) {                                                                               \
      nmethod*  osr_nmethod;                                                                    \
      OSR_REQUEST(osr_nmethod, branch_pc);                                                      \
      if (osr_nmethod != NULL && osr_nmethod->osr_entry_bci() != InvalidOSREntryBci) {          \
        intptr_t* buf = SharedRuntime::OSR_migration_begin(THREAD);                             \
        istate->set_msg(do_osr);                                                                \
        istate->set_osr_buf((address)buf);                                                      \
        istate->set_osr_entry(osr_nmethod->osr_entry());                                        \
        return;                                                                                 \
      }                                                                                         \
    }                                                                                           \
  }  /* UseCompiler ... */                                                                      \
  INCR_INVOCATION_COUNT;                                                                        \
  SAFEPOINT;                                                                                    \
  }



インタプリタのローカル領域を対比する。::

  // Allocate temp buffer, 1 word per local & 2 per active monitor
  int buf_size_words = max_locals + active_monitor_count*2;
  intptr_t *buf = NEW_C_HEAP_ARRAY(intptr_t,buf_size_words);

  // Copy the locals.  Order is preserved so that loading of longs works.
  // Since there's no GC I can copy the oops blindly.
  assert( sizeof(HeapWord)==sizeof(intptr_t), "fix this code");
  Copy::disjoint_words((HeapWord*)fr.interpreter_frame_local_at(max_locals-1),
    (HeapWord*)&buf[0],
    max_locals);


c1のOSR
===============================================================================

memo ::

  c1 LIR Assembler
  void LIR_Assembler::osr_entry() {

  offsets()->set_value(CodeOffsets::OSR_Entry, code_offset());
  BlockBegin* osr_entry = compilation()->hir()->osr_entry();
  ValueStack* entry_state = osr_entry->state();
  int number_of_locks = entry_state->locks_size();
  
  // we jump here if osr happens with the interpreter
  // state set up to continue at the beginning of the
  // loop that triggered osr - in particular, we have
  // the following registers setup:
  //
  // rcx: osr buffer
  //

  // build frame
  ciMethod* m = compilation()->method();
  __ build_frame(initial_frame_size_in_bytes());


comment ::

  // OSR buffer is
  //
  // locals[nlocals-1..0]
  // monitors[0..number_of_locks]
  //
  // locals is a direct copy of the interpreter frame so in the osr buffer
  // so first slot in the local array is the last local from the interpreter
  // and last slot is local[0] (receiver) from the interpreter
  //
  // Similarly with locks. The first lock slot in the osr buffer is the nth lock
  // from the interpreter frame, the nth lock slot in the osr buffer is 0th lock
  // in the interpreter frame (the method lock if a sync method)
  
  // Initialize monitors in the compiled activation.
  //   rcx: pointer to osr buffer
  //
  // All other registers are dead at this point and the locals will be
  // copied into place by code emitted in the IR.



rcxのpointer to osr buffer以外の 全レジスタはdeadですと仮定すると。

でもそれってOSR対応メソッドのレジスタ割り付けはあほだってことですか。


