intrinsics
###############################################################################

C2のintrinsics
===============================================================================

JVMのintrinsicsは、 classfile/vmSymbolsに定義されている。

c2コンパイラの場合、以下で各々のシンボルに変換される。

opto/doCall optoコンパイラ内部のシンボルに変換

opto/library_call.cpp  macroasm向けのシンボルに変換

arrayCopy
===============================================================================

jvmのarrayCopyは、x86archの下に、複数の型ごとにarrayCopyのgeneratorを用意していて、

JITコンパイル時にgeneratorを切り替えている。

generatorの下では、loopalignされた状態でcopyするっぽい。

mmxとxmmの切り替えは実行時に行う見たいだけど、SSEやAVX向けはなし。


