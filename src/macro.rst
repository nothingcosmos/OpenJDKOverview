
OpenJDK opto macro
###############################################################################

macro
*******************************************************************************

optp/macroでは、

optoのメモリアロケーションやlock/unlock barrier系の特殊な操作がmacroとして定義されている。

OpenJDK7からは、TLAB(Thread local allocation buffer)関連を上手く使うためのmacroも追加されていたかな？

メモ書き
===============================================================================


PrefetchReadNode

PrefetchWriteNode

PrefetchAllocationNode

どこかで、zero initializeしないallocatorが追加されていたはず。


関連option ::
  
  product(intx, TrackedInitializationLimit, 50,                             \
  "When initializing fields, track up to this many words")          \
  \
  product(bool, ReduceFieldZeroing, true,                                   \
  "When initializing fields, try to avoid needless zeroing")        \
  \
  product(bool, ReduceInitialCardMarks, true,                               \
  "When initializing fields, try to avoid needless card marks")     \
  \
  product(bool, ReduceBulkZeroing, true,                                    \
  "When bulk-initializing, try to avoid needless zeroing")          \
  






プリフェッチ
===============================================================================

x86の場合、prefetcht0や、prefetchntaを自動挿入する。

System.out.printf("%d\n", sum) ::

  59   B7: #     B23 B8 <- B14 B6  Freq: 0.999983
  059     MOV    ECX, Thread::current()
  065     MOV    EAX,[ECX + #68]
  068     LEA    EBX,[EAX + #16]
  06b     MOV    EDI,java/lang/Class:exact *
  070     MOV    EBP,[EDI + #108] ! Field java/lang/System.out
  073     MOV    [ESP + #16],EBP
  077     CMPu   EBX,[ECX + #76]
  07a     Jnb,u  B23  P=0.000100 C=-1.000000
  07a
  080   B8: #     B9 <- B7  Freq: 0.999883
  080     MOV    [ECX + #68],EBX
  083     PREFETCHNTA [EBX + #192]        ! Prefetch into non-temporal cache for write
  08a     MOV    [EAX],0x00000001
  090     PREFETCHNTA [EBX + #256]        ! Prefetch into non-temporal cache for write
  097     MOV    [EAX + #4],precise klass [Ljava/lang/Object;: 0x7ce47098:Constant:exact *
  09e     PREFETCHNTA [EBX + #320]        ! Prefetch into non-temporal cache for write
  0a5     MOV    [EAX + #8],#1
  0ac     PREFETCHNTA [EBX + #384]        ! Prefetch into non-temporal cache for write
  0b3     MOV    [EAX + #12],#0





===============================================================================


OpenJDK8
===============================================================================

以下のMemBarが新規追加されている。

MemBarStoreStoreNode

MemBarReleaseLockNode

MemBarAcquireLockNode


