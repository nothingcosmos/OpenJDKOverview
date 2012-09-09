todo
###############################################################################

On-Stack Replacement and Deoptimization
===============================================================================

の詳細をもうちょっと追ってみたい


superword
===============================================================================

superwordという名前で実装されている、C2コンパイラのベクトル化の詳細

最終的には、x86のSSEへ落とすらしい。

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

Sharkには、On-Stack ReplacementとDirect Devirtualizationを実装している。

でも、GCのSafepointで問題が発生しているとか。

あと、Deoptimizationで問題発生していないだろうか。

-------------------------------------------------------------------------------

