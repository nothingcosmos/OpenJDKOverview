C1コンパイラのHIR最適化
###############################################################################

HIR Optimizationは、BytecodeからHIRへ変換終わった後に行う

c1_Compilation.cpp::build_hir() ::

  _hir->optimize();
    IR::optimize()
      opt.eliminate_conditional_expressions();
      opt.eliminate_blocks();
      opt.eliminate_null_checks();

eliminate_conditional_expressions
===============================================================================

@todo 時間があればコードを追うこと

CE_Eliminator::block_do()が本体 ::

  ブロックの終端のifを取得
  ifはint型かobject型か判定

  true  block
  false block
  true_instruction
  false_instruction

  a = (b > c) ? b : c;

  BB:
    if ... then BBn false BBm
  BBn:
    const n
    goto BBs
  BBm:
    const m
    goto BBs
  BBs:
    phi [][]


CEE Java::

  public static int CETest(int ret, int n,int  m) {
    ret += (n < m) ? n : m;
    return ret;
  }

CEE HIR ::

  B62:
    i179 = i103 - i76
    i180 = i178 - i151
    v186 = if i179 > i180 then B73 else B72  <-- replace  i186 = ifop (i179 > i180) ixx, iyy;
                                             <-- add      goto B74
  B73:                                       <-- delete
    v188 = goto B74                          <-- delete
  B72:                                       <-- delete
    v187 = goto B74                          <-- delete
  B74:
    i189 = [i179,i180]                       <-- replace
    v190 = goto B70
  B70:
    i191 = i151 + i189


GlobalValueNumbering
===============================================================================

@todo 時間があればコードを追うこと

c1_ValueMap.hpp::GlobalValueNumbering(IR* ir) ::

  ShortLoopOptimizer short_loop_optimizer(this);

  for (int i = 1; i < num_blocks; i++) {
    BlockBegin* block = blocks->at(i);

    ...

    if (num_preds == 1) {
      // nothing to do here

    } else if (block->is_set(BlockBegin::linear_scan_loop_header_flag)) {
      // block has incoming backward branches -> try to optimize short loops
      if (!short_loop_optimizer.process(block)) {          <-- ループは特別に処理
        // loop is too complicated, so kill all memory loads because there might be
        // stores to them in the loop
        current_map()->kill_memory();
      }

    } else {
      // only incoming forward branches that are already processed
      for (int j = 0; j < num_preds; j++) {
        BlockBegin* pred = block->pred_at(j);
        ValueMap* pred_map = value_map_of(pred);

        if (pred_map != NULL) {
          // propagate killed values of the predecessor to this block
          current_map()->kill_map(value_map_of(pred));
        } else {
          // kill all memory loads because predecessor not yet processed
          // (this can happen with non-natural loops and OSR-compiles)
          current_map()->kill_memory();
        }
      }
    }

    if (block->is_set(BlockBegin::exception_entry_flag)) {
      current_map()->kill_exception();
    }

    TRACE_VALUE_NUMBERING(tty->print("value map before processing block: "); current_map()->print());

    // visit all instructions of this block
    for (Value instr = block->next(); instr != NULL; instr = instr->next()) {
      assert(!instr->has_subst(), "substitution already set");

      // check if instruction kills any values
      instr->visit(this);

      if (instr->hash() != 0) {
        Value f = current_map()->find_insert(instr);  <-- ここがキモ
        if (f != instr) {                        
          assert(!f->has_subst(), "can't have a substitution");
          instr->set_subst(f);
          subst_count++;    
        } 
      }
    }

    // remember value map for successors
    set_value_map_of(block, current_map());      
  }


EscapeAnalysis
===============================================================================

c1コンパイラからは呼ばれません！！！

wimmerの資料によるとclientからも呼ばれるようだが、昔のJDKもしくはSun JDKだけなのかもしれない。

Serverだとbreakを確認できた。

Sharkだとifdef切ってるけど、多分動かないんだろうと推測


C1コンパイラのLIR最適化
###############################################################################

EdgeMoveOptimizer
===============================================================================

c1_LinearScan.cpp::EdgeMoveOptimizer::optimize(BlockList* code) ::

  EdgeMoveOptimizer optimizer = EdgeMoveOptimizer();

  // ignore the first block in the list (index 0 is not processed)
  for (int i = code->length() - 1; i >= 1; i--) {
    BlockBegin* block = code->at(i);

    if (block->number_of_preds() > 1 && !block->is_set(BlockBegin::exception_entry_flag)) {
      optimizer.optimize_moves_at_block_end(block);
    }
    if (block->number_of_sux() == 2) {
      optimizer.optimize_moves_at_block_begin(block);
    }
  }

preds > 1 --> ブロックへのjumpが複数ある場合

  CFGの合流ブロックとか、loopのheaderとか

  code sink みたいな処理


sux == 2 --> ブロックからのjumpが複数ある場合

  分岐とか、loopのback_edgeとか

  code hoist みたいな処理

ControlFlowOptimizer
===============================================================================

c1_LiearScan.cpp::ControlFlowOptimize::optimize(BlockList* code) ::

  ControlFlowOptimizer optimizer = ControlFlowOptimizer();

  // push the OSR entry block to the end so that we're not jumping over it.
  BlockBegin* osr_entry = code->at(0)->end()->as_Base()->osr_entry();
  if (osr_entry) {
    int index = osr_entry->linear_scan_number();
    assert(code->at(index) == osr_entry, "wrong index");
    code->remove_at(index);
    code->append(osr_entry);
  }

  optimizer.reorder_short_loops(code);
  optimizer.delete_empty_blocks(code);
  optimizer.delete_unnecessary_jumps(code);
  optimizer.delete_jumps_to_return(code);


reorder_short_loops()

下記のようなケースをpost jumpっぽくする

end_block ::

  B4 (V) [35, 50] -> B3 dom B3 sux: B3 pred: B3

  __bci__use__tid____instr____________________________________
  35   1    i19    31
  38   1    i20    i15 * i19
  41   2    i21    1
  . 41   1    i22    i16 + i21
  . 44   1    i23    a11[i16] (C)
  . 45   1    i24    i20 + i23
  . 47   1    i26    i17 + i21
  . 50   0     27    goto B3 (safepoint)

header_block ::

  B3 (LHbV) [28, 32] -> B5 B4 dom B1 sux: B5 B4 pred: B1 B4

  __bci__use__tid____instr____________________________________
  . 32   0     18    if i17 >= i12 then B5 else B4


delete_unnecessary_jumps()

  last_branchが次のブロックへの飛び先だったらdelete

  cmp branch1 branch2の場合、branch1が次のブロックへのjumpだったら、
  branch1を書き換えて、condの条件を反転 branch2を削除


