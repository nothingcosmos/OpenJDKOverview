todo
###############################################################################

今後の予定

C2 Devirtualization
===============================================================================

optpの中の、Devirtualizationの詳細

主にopto/doCall.cppに記述

EscapeAnalysis
===============================================================================

opto/escapeの詳細

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


superword
===============================================================================

superwordという名前で実装されている、C2コンパイラのベクトル化の詳細

opto/superword.hpp ::

  //                  S U P E R W O R D   T R A N S F O R M
  //
  // SuperWords are short, fixed length vectors.
  //
  // Algorithm from:
  //
  // Exploiting SuperWord Level Parallelism with
  //   Multimedia Instruction Sets
  // by
  //   Samuel Larsen and Saman Amarasighe
  //   MIT Laboratory for Computer Science
  // date
  //   May 2000
  // published in
  //   ACM SIGPLAN Notices
  //   Proceedings of ACM PLDI '00,  Volume 35 Issue 5


Shark
===============================================================================

vm/sharkの詳細 全体で11kstep程度

sharkは、BytecodeをLLVM IRへ変換したのち、LLVMを使用してJITコンパイルするモジュール

inline展開およびdevirtualizationは、sharkモジュール中で行う。

必要なstabの挿入は、LLVM IRへの変換の際に行う。

-------------------------------------------------------------------------------
-------------------------------------------------------------------------------
-------------------------------------------------------------------------------

