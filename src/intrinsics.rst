intrinsics
###############################################################################

JVMはcpu依存の機能を提供するため、組込み関数(以降intrinsics)を定義している。

JVMはC++で開発されているため、JavaからJNIのnativeを使用してintrinsicsを呼び出す。

例をあげると、compare-and-swap.
class fileからはnative関数として呼び出され、JVM側でnativeな関数を定義している。

JVMはintrinsicsをcpu非依存に提供する。intrinsicsを提供する処理はおもに2種類ある。

(1) cpu依存の特殊な処理
(2) cpu依存で高速化している処理

cpu依存は2種類に分けられて、

(1) cpuのアーキテクチャ(arch)の違い。x86の場合はcmpxchg命令、sparcの場合はcasx命令など。
(2) 同じarchのInstruction Set Architectureの違い。cpuによって実装されていない命令は、他命令群で代替する。

cpu依存で高速化している処理は、mmxやsseがある場合、それ向けの高速な処理を使用する。

例をあげると、sse4を使用して128bitずつのarray copyやcompareなど。

JVMはOSとArchごとにbuildされ提供されているが、cpuのISAごとには提供されない。

そのため、ISA依存の処理はJVMの起動時に生成する。のでは？

ここまで一般的な話
===============================================================================

compare-and-swapを例に処理を追っていたのですが、
x86のcmpxchgのISAはi486であり、JVMの前提ISAはi586なので、
インタプリタからもJITコンパイラからも自由に呼び出せるようです。

昔のi386向けのJVMだったら、cmpxchgがISA依存で処理が分かれていたかもしれませんね。。


#ParaConBase では、JVM起動時にこれらのCPU依存処理を生成して、
インタプリタやJITコンパイルしたコードから飛び込むみたいなことを言いましたが、
Dart VMの話とごっちゃになっていたようです。。

起点
===============================================================================
compareAndSwapIntと、cmpxchgを例に追ってみます。

penjdk7u/jdk/src/share/classes/sun/misc/Unsafe.java ::

  public final native boolean compareAndSwapInt(Object o, long offset,
                                                int expected,
                                                int x);


compareAndSwapIntを探す。
===============================================================================

openjdk7u/hotspot/src/share/vm ::

  //unsafeのnativeの実装はすべてここ。
  prims/unsafe.cpp:    {CC"compareAndSwapInt",  CC"("OBJ"J""I""I"")Z",      FN_PTR(Unsafe_CompareAndSwapInt)},
  prims/unsafe.cpp:    {CC"compareAndSwapInt",  CC"("OBJ"J""I""I"")Z",      FN_PTR(Unsafe_CompareAndSwapInt)},
    runtime/atomic.cpp:Atomic::cmpxchg(x, addr, e)
      Atomic::cmpxchg(jint, uintptr_t, jint) <-- これの定義がどこにあるのか不明だった。

  //JVMのintrinsicsを定義してる(実装はマクロで展開する)
  classfile/vmSymbols.hpp:  do_intrinsic(_compareAndSwapInt,        sun_misc_Unsafe,        compareAndSwapInt_name, compareAndSwapInt_signature, F_RN) \
  classfile/vmSymbols.hpp:   do_name(     compareAndSwapInt_name,                          "compareAndSwapInt")                                   \
  classfile/vmSymbols.hpp:   do_signature(compareAndSwapInt_signature,                     "(Ljava/lang/Object;JII)Z")                            \

  //c1はclient JITコンパイラ用のディレクトリ
  c1/c1_GraphBuilder.cpp:    case vmIntrinsics::_compareAndSwapInt:
  c1/c1_LIRGenerator.cpp:  case vmIntrinsics::_compareAndSwapInt:

  //optoはServer JITコンパイラ用のディレクトリ
  opto/library_call.cpp:  case vmIntrinsics::_compareAndSwapInt:        return inline_unsafe_load_store(T_INT,    LS_cmpxchg);
  opto/library_call.cpp://   public final native boolean compareAndSwapInt(   Object o, long offset, int    expected, int    x);

  //sharkはJVMのJITコンパイラにLLVMを使用するwrapper
  shark/sharkIntrinsics.cpp:  case vmIntrinsics::_compareAndSwapInt:
  shark/sharkIntrinsics.cpp:  case vmIntrinsics::_compareAndSwapInt:
  shark/sharkIntrinsics.cpp:    do_Unsafe_compareAndSwapInt();
  shark/sharkIntrinsics.cpp:void SharkIntrinsics::do_Unsafe_compareAndSwapInt() {
  shark/sharkIntrinsics.hpp:  void do_Unsafe_compareAndSwapInt();

cmpxchg
===============================================================================
cmpxchgは、linuxの場合、以下のファイルで定義されています。(ia32版)

この段階で、cmpxchgがISAごとに定義がわかれていないことに気づく。。

openjdk7u/hotspot/src/os_cpu/linux_x86/vm/atomic_linux_x86.inline.hpp ::

  inline jint     Atomic::cmpxchg    (jint     exchange_value, volatile jint*     dest, jint     compare_value) {
    int mp = os::is_MP();
    __asm__ volatile (LOCK_IF_MP(%4) "cmpxchgl %1,(%3)"
                      : "=a" (exchange_value)
                      : "r" (exchange_value), "a" (compare_value), "r" (dest), "r" (mp)
                      : "cc", "memory");
    return exchange_value;
  }

JVMはbytecodeをインタプリタ実行するはずです。

おそらくインタプリタ実行の際にcompareAndSwapIntの呼び出しがあれば、
最終的に上記で定義されているAtomic::cmpxchgを呼び出すはずです。


じゃぁ他の定義はなんだったのか。。
===============================================================================

x86の場合、最終的にcmpxchg命令が呼ばれますが、その経路は4種類あります。

(1) インタプリタ実行時に呼び出す処理
(2) client JITコンパイラでコンパイルされたコードに埋め込む処理
(3) server JITコンパイラでコンパイルされたコードに埋め込む処理
(4) shark JITコンパイラでコンパイルされたコードに埋め込む処理

vmSymbols
===============================================================================

classfile/vmSymbols.hppでは、JVMの組込み関数が定義されており、
bytecodeをJITコンパイルする際に、intrinsics呼び出しとして扱います。

classfile/vmSymbols.hpp ::

  do_intrinsic(_compareAndSwapInt,        sun_misc_Unsafe,        compareAndSwapInt_name, compareAndSwapInt_signature, F_RN) \
  do_name(     compareAndSwapInt_name,                          "compareAndSwapInt")                                   \
  do_signature(compareAndSwapInt_signature,                     "(Ljava/lang/Object;JII)Z")                            \

上記の文字列にマッチすると、JVM内ではvmIntrinsics::_compareAndSwapIntの組込み関数呼び出しと扱います。

おそらく、invoke->intrinsc_id() == vmIntrinsics::_compareAndSwapInt

client JITコンパイラ
===============================================================================

C1はclient JITコンパイラ ::

  c1/c1_GraphBuilder.cpp:
    case vmIntrinsics::_compareAndSwapInt:
    case vmIntrinsics::_compareAndSwapObject:
      append_unsafe_CAS(callee);
        Intrinsic* result = new Intrinsic(result_type, callee->intrinsic_id(), args, false, state_before, preserves_state);
      return true;

  c1/c1_LIRGenerator.cpp:
    case vmIntrinsics::_compareAndSwapInt:
      do_CompareAndSwap(x, intType);
      break;

  src/cpu/x86/vm/c1_LIRGenerator_x86.cpp
    void LIRGenerator::do_CompareAndSwap(Intrinsic* x, ValueType* type) {
    ...
      } else if (type == intType) {
        cmp.load_item_force(FrameMap::rax_opr);
        val.load_item();
    ...
      else if (type == intType)
        __ cas_int(addr, cmp.result(), val.result(), ill, ill);
    ...

  src/share/vm/c1/c1_LIR.cpp
    void LIR_List::cas_int(LIR_Opr addr, LIR_Opr cmp_value, LIR_Opr new_value,
                           LIR_Opr t1, LIR_Opr t2, LIR_Opr result /*=LIR_OprFact::illegalOpr*/) {
      append(new LIR_OpCompareAndSwap(lir_cas_int, addr, cmp_value, new_value, t1, t2, result));
    }

  LIR_OpCompareAndSwapは、c1コンパイラのLIRノードなので、別途emit_code()が走ってアセンブラを生成する。
  src/share/vm/c1/c1_LIR.cpp
    void LIR_OpCompareAndSwap::emit_code(LIR_Assembler* masm) {
      masm->emit_compare_and_swap(this);
    }


  src/cpu/x86/vm/c1_LIRAssembler_x86.cpp
    void LIR_Assembler::emit_compare_and_swap(LIR_OpCompareAndSwap* op) {
      ...
      assert(op->code() == lir_cas_int, "lir_cas_int expected");
      if (os::is_MP()) {
        __ lock();
      }
      __ cmpxchgl(newval, Address(addr, 0));

  やっとcmpxchg命令がemitされたよ。。


デバッガで追ったわけではaりませんが、こんな感じだと思われます。

サンプルコードを生成してアセンブリ命令を出力してみます。

CAS.java ::

  import java.util.concurrent.atomic.*;
  public class CAS{
    public static void main (String[] args) {
      AtomicInteger ai = new AtomicInteger();
      for (int i=0; i<1000000000; i++) {
        kernel(ai);
      }
      System.out.println("ret = " + ai.get() + "\n");
    }
    public static int kernel(AtomicInteger ai) {
      return ai.incrementAndGet();
    }
  }

  こんな感じで、LIRにcas_intに置き替えられて、それがlock cmpxchgになっている。

  12 label [label:0x9fb889ac]
  14 move [edx|I] [eax|I] 
  0xb46a739f: mov    %edx,%eax
  16 leal [Base:[ecx|L] Disp: 8|I] [edi|I] 
  0xb46a73a1: lea    0x8(%ecx),%edi
  18 cas_int [edi|I] [eax|I] [esi|I]   
  0xb46a73a4: lock cmpxchg %esi,(%edi)
  20 cmove [EQ] [int:1|I] [int:0|I] [eax|I]
  0xb46a73a8: mov    $0x1,%eax
  0xb46a73ad: je     0xb46a73b8
  0xb46a73b3: mov    $0x0,%eax
  24 return [eax|I]  
  0xb46a73b8: add    $0x28,%esp
  0xb46a73bb: pop    %ebp
  0xb46a73bc: test   %eax,0xb770b100
  0xb46a73c2: ret    

結論として、client JITコンパイルしたコードに、cmpxchgは埋め込まれる。。

server JITコンパイラ
===============================================================================
optoはServer JITコンパイラ ::

  opto/library_call.cpp:  case vmIntrinsics::_compareAndSwapInt:        return inline_unsafe_load_store(T_INT,    LS_cmpxchg);
  opto/library_call.cpp://   public final native boolean compareAndSwapInt(   Object o, long offset, int    expected, int    x);

          # CAS::kernel @ bci:1  L[0]=_
          # OopMap{ecx=Oop ebx=Derived_oop_ecx ebp=Oop off=60}
  042     MOV    EAX,[ECX + #8]   # int ! Field  Volatilejava/util/concurrent/atomic/AtomicInteger.value
  045     MEMBAR-acquire ! (empty encoding)
  045     MEMBAR-release ! (empty encoding)
  045
  045     MOV    ECX,EAX
  047     INC    ECX
  048     CMPXCHG [EBX],ECX       # If EAX==[EBX] Then store ECX into [EBX]
          MOV    EDI,0
          JNE,s  fail
          MOV    EDI,1
  fail:
  058
  058     MEMBAR-acquire ! (empty encoding)
  058     TEST   EDI,EDI
  05a     Je,s  B6  P=0.000000 C=46393.000000

serverコンパイラの場合、コンパイラ内部の取扱いはもっと複雑なのですが、

出力は似たようにJITコンパイルしたコードに、cmpxchgを埋め込みます。

shark JITコンパイラ
###############################################################################

sharkは、JVMのJITコンパイルにLLVMを使用するためのwrapperです。

JITコンパイルする際に、bytecodeからbitcode(LLVM IRってやつ)に変換して、

LLVM IRをLLVMにJITコンパイルさせて、アセンブラを出力します。

  shark/sharkIntrinsics.cpp ::

  void SharkIntrinsics::do_Unsafe_compareAndSwapInt() {
    // Pop the arguments
    Value *x      = state()->pop()->jint_value();
    Value *e      = state()->pop()->jint_value();
    SharkValue *empty = state()->pop();
    assert(empty == NULL, "should be");
    Value *offset = state()->pop()->jlong_value();
    Value *object = state()->pop()->jobject_value();
    Value *unsafe = state()->pop()->jobject_value();
  
    // Convert the offset
    offset = builder()->CreateCall(
      builder()->unsafe_field_offset_to_byte_offset(),
      offset);
  
    // Locate the field
    Value *addr = builder()->CreateIntToPtr(
      builder()->CreateAdd(
        builder()->CreatePtrToInt(object, SharkType::intptr_type()),
        builder()->CreateIntCast(offset, SharkType::intptr_type(), true)),
      PointerType::getUnqual(SharkType::jint_type()),
      "addr");
  
    // Perform the operation
    Value *result = builder()->CreateCmpxchgInt(x, addr, e); <--  これは！！！
  
    // Push the result
    state()->push(
      SharkValue::create_jint(
        builder()->CreateIntCast(
          builder()->CreateICmpEQ(result, e), SharkType::jint_type(), true),
        false));
  }

上記のようにLLVM IRをbuilderを使って作成するのですが、きもは、CreateCmpxchgInt()のところ。

LLVMの組込み関数 llvm.atomic.cmp.swap.i32.p0i32 を内部で生成しています。

LLVMがJITコンパイルする際に、対象アーキテクチャのcas命令を生成してくれるはずです。

MacroAssembler
###############################################################################
ここからは上記とあまり関係がないメモ書きです。

C2のintrinsics
===============================================================================

JVMのintrinsicsは、 classfile/vmSymbolsに定義されている。

c2コンパイラの場合、以下で各々のシンボルに変換される。

opto/doCall optoコンパイラ内部のシンボルに変換

opto/library_call.cpp  Macro Assembler向けのシンボルに変換

cpu/vm/x86/xxx  Macro Assemblerの定義がある。

arrayCopy
===============================================================================

jvmのarrayCopyは、x86archの下に、複数の型ごとにarrayCopyのgeneratorを用意していて、

JITコンパイル時にgeneratorを切り替えている。

generatorの下では、loopalignされた状態でcopyするっぽい。

mmxとxmmの切り替えは実行時に行う見たいだけど、SSEやAVX向けはなし。


