BytecodeEscapeAnalysis
###############################################################################

HotSpotには、2種類のEscapeAnlaysisを実装している。

ci/bcEscapeAnalyzer
-------------------------------------------------------------------------------

oops中のBytecodeを解析する


opto/escape
-------------------------------------------------------------------------------

Idealを解析する

opto/escape.hpp ::

  //
  // Adaptation for C2 of the escape analysis algorithm described in:
  //
  // [Choi99] Jong-Deok Shoi, Manish Gupta, Mauricio Seffano,
  //          Vugranam C. Sreedhar, Sam Midkiff,
  //          "Escape Analysis for Java", Procedings of ACM SIGPLAN
  //          OOPSLA  Conference, November 1, 1999
  //
  // The flow-insensitive analysis described in the paper has been implemented.
  //
  // The analysis requires construction of a "connection graph" (CG) for
  // the method being analyzed.  The nodes of the connection graph are:
  //
  //     -  Java objects (JO)
  //     -  Local variables (LV)
  //     -  Fields of an object (OF),  these also include array elements
  //
  // The CG contains 3 types of edges:
  //
  //   -  PointsTo  (-P>)    {LV, OF} to JO
  //   -  Deferred  (-D>)    from {LV, OF} to {LV, OF}
  //   -  Field     (-F>)    from JO to OF


ci/bcEscapeAnalyzerの詳細
===============================================================================

opto/escapeから補助的に呼ばれ、 bytecodeのstore命令や、put等を解析する。

IdealのCallNodeがstaticだった場合に、BCEscapeAnalyzerを使用する

opto/escape.cpp ::

  case Op_CallStaticJava:
  // For a static call, we know exactly what method is being called.
  // Use bytecode estimator to record the call's escape affects
  {
    ciMethod *meth = call->as_CallJava()->method();
    BCEscapeAnalyzer *call_analyzer = (meth !=NULL) ? meth->get_bcea() : NULL;

Bytecodesをブロック単位でイテレートしながら、解析する

set_xxx()でフラグ操作が追えるけど、なんかの論文読んだ方が詳細を理解するのは早いかも


opto/escapeの詳細
===============================================================================

あとで


EscapeAnalysisの解析結果
===============================================================================

UnknownEscape

GlobalEscape

ArgEscape

NoEscape

EscapeAnalysisの解析結果の活用方法
===============================================================================

EscapeAnalysisの活用方法は、2つある。

解析結果であるEscapeStateは、Idealのnode->escape_state() で取得できる。

(1) LockNode/UnlockNodeの削除

  callnode.cppに処理が記述してある。

  LockやUnlockのNodeは、対象のオブジェクトがGlobalEscapeで無ければ削除可能である

(2) is_scalar_replaceable()

  opto/escape.cpp:compute_escape()において、EscapeAnalysisが終わった後、split_unique_types()でis_scalar_replaceableを立てて歩く

  後々のフェーズであるPhaseMacroExpand::eliminate_allocate_node()の解析が走り、可能であればallocを削除する

  bool PhaseMacroExpand::scalar_replacement()

  オプションで様子を眺める場合、PrintEliminateAllocationsで可能なはず


===============================================================================
===============================================================================
