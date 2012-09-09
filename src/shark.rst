
Shark
###############################################################################

Sharkは、OpenJDKの第3のJITコンパイラです。

Sharkは、LLVMを利用して、LLVMがサポートする各種プラットフォーム向けのAsmを生成します。

役割分担としては、JITコンパイラに必要なRuntime向けのIFの提供をSharkが行い、
コンパイラとしてのコード生成をLLVM側が担当します。

Sharkの概要
*******************************************************************************

Sharkは、JavaのbytecodeをLLVMIRに変換し、LLVMIRのコンパイルをLLVMに依頼し、Asmを取得します。

Shark全体で12k lineです。

Sharkの問題点

HotSpotcompilerはGCにoopはこのレジスタにあるよという指示を出せるが、

これはLLVMでは出来ない為、一旦メモリに落とす。これに関連する処理のオーバーヘッドが大きいとかなんとか。

GHC LLVMBackend
*******************************************************************************

GHCは、GHC内部の低レベル中間表現であるC--から、LLRMIRへ変換し、Asmを生成します。

GHCのLLVMIR向けCodegenは、2.2k程度のHaskellでかかれているとか。

内包は、cmmから、llファイルへのpretty printするのみ。

GHCのCommon CodeGen自体が11kと比較すると、非常にコンパクトです。

ソースコード
===============================================================================

code ::

  llvmHeaders.hpp
  llvmValue.hpp
  sharkBlock.cpp
  sharkBlock.hpp
  sharkBuilder.cpp
  sharkBuilder.hpp
  sharkCacheDecache.cpp
  sharkCacheDecache.hpp
  sharkCodeBuffer.hpp
  sharkCompiler.cpp
  sharkCompiler.hpp
  sharkConstant.cpp
  sharkConstant.hpp
  sharkContext.cpp
  sharkContext.hpp
  sharkEntry.hpp
  sharkFunction.cpp
  sharkFunction.hpp
  sharkInliner.cpp
  sharkInliner.hpp
  sharkIntrinsics.cpp
  sharkIntrinsics.hpp
  sharkInvariants.cpp
  sharkInvariants.hpp
  sharkMemoryManager.cpp
  sharkMemoryManager.hpp
  sharkNativeWrapper.cpp
  sharkNativeWrapper.hpp
  sharkRuntime.cpp
  sharkRuntime.hpp
  sharkStack.cpp
  sharkStack.hpp
  sharkState.cpp
  sharkState.hpp
  sharkStateScanner.cpp
  sharkStateScanner.hpp
  sharkTopLevelBlock.cpp
  sharkTopLevelBlock.hpp
  sharkType.hpp
  sharkValue.cpp
  sharkValue.hpp
  shark_globals.cpp
  shark_globals.hpp


Sharkの処理概要
*******************************************************************************



compile_method
===============================================================================

code ::

  void SharkCompiler::compile_method(ciEnv*    env, ciMethod* target, int       entry_bci);
  
  
  typeflow analysis
  
    get_flow_analysis()
    get_osr_flow_analysis()
  
    SharkFunctin::build()
      SharkFunction()
        initialize()
        
      //SharkTargetInvariants()
      
    generate_native_code()
  

SharkFunction::initialize(name)
===============================================================================

code ::

  Function::arg_iterator Argument(method, osr_buf, base_pc, thread)
  
  new SharkTopLevelBlock()
  start_block->enter()
  
  
  block(i)->initialize()
  
  if (is_osr()) {
    entry_state = new SharkOSREntryState()
    builder()->CreateCall()
  
  typeFlow


generate_native_code()
===============================================================================



===============================================================================

===============================================================================

.. image:: xxx.png
..
.. literalinclude:: xxx.js
..
.. graphviz::
..
..
..xxx ::
..
..  xxx
..
.. code-block:: python
..    :linenos:
..
.. literalinclude:: src/test.py
..
..  :language: python
..  :linenos:


