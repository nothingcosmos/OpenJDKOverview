BytecodeEscapeAnalysis
###############################################################################

ci/bcEscapeAnalyzer
===============================================================================

opto/escape.cpp ::

  case Op_CallStaticJava:
  // For a static call, we know exactly what method is being called.
  // Use bytecode estimator to record the call's escape affects
  {
    ciMethod *meth = call->as_CallJava()->method();
    BCEscapeAnalyzer *call_analyzer = (meth !=NULL) ? meth->get_bcea() : NULL;

Bytecodesをブロック単位でイテレートしながら、解析する

set_xxx()でフラグ操作が追えるけど、なんかの論文読んだ方が詳細を理解するのは早いかも

EscapeAnalysisの解析結果
===============================================================================

UnknownEscape

GlobalEscape

ArgEscape

NoEscape

EscapeAnalysisの解析結果の用途
===============================================================================

EscapeStateは、node->escape_state() で取得できる。

(1) LockNode/UnlockNodeの削除

  callnode.cpp

  GlobalEscapeで無ければ削除しちゃう

(2) is_scalar_replaceable()

  compute_escape()で、EscapeAnalysisが終わった後、split_unique_types()でis_scalar_replaceableを立てて歩く

  最終的には、PhaseMacroExpand::eliminate_allocate_node()の解析が走り、可能であればallocを削除する

  bool PhaseMacroExpand::scalar_replacement()

  オプションで様子を眺める場合、PrintEliminateAllocationsで可能なはず

他にも、connectingGraphっていうクラスが専用のメソッドを提供している???


===============================================================================
===============================================================================
