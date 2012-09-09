Devirtualization
###############################################################################

C1とC2のDevirtualizeは異なるので、詳細を比較する

C1では、abstract methodのDevirtualizeが行われないなど、違いがある

share/c1/c1_GraphBuilder.cpp::invoke() ::

  if (cha_monomorphic_target != NULL) {
    if (cha_monomorphic_target->is_abstract()) {
      // Do not optimize for abstract methods
      cha_monomorphic_target = NULL;
    }
  }

ポイントとなるメソッド
===============================================================================

C1での概念

  cha_monomorphic_target

  exact_target

C1で使用したメソッド

  ciMethod* target; <-- src/share/vm/ci

  target->resolve_invoke()

  target->find_monomorphic_target()


脱最適化の予約

  dependency_recorder()->assert_xxx(receiver_klass)

C2(opto)でのDevirtualization
===============================================================================

file opto/doCall.cpp

Compile::call_generator() ::

  doCall.cpp: receiver_method = call_method->resolve_invoke(jvms->method()->holder(),
  doCall.cpp: next_receiver_method = call_method->resolve_invoke(jvms->method()->holder(),

Parse::optimize_inlining() ::

  doCall.cpp: ciMethod* exact_method = dest_method->resolve_invoke(calling_klass, actual_receiver);
  doCall.cpp: ciMethod* cha_monomorphic_target = dest_method->find_monomorphic_target(calling_klass, klass, actual_receiver);

optimize_inlining()
-------------------------------------------------------------------------------

シンプルというか、C1より大雑把な見立てでDevirtualizeを決める

call_generator()
-------------------------------------------------------------------------------

TypeProfileMajorReceiverPercent 90%

TypeProfileしながら、90%以上だったらdevirtualizeしてる

複数の可能性があっても、Devirtualizeを試行する

receiver_count > 0 のcode ::

  // Try using the type profile.
  if (call_is_virtual && site_count > 0 && receiver_count > 0) {
    // The major receiver's count >= TypeProfileMajorReceiverPercent of site_count.
    // 初期オプションでは、90%以上
    bool have_major_receiver = (100.*profile.receiver_prob(0) >= (float)TypeProfileMajorReceiverPercent);
    ciMethod* receiver_method = NULL;

    // (1) 90%以上
    // (2) 候補が1つだけ
    // (3) 候補が2つだけ、かつオプションで候補2つの脱仮想化+Inliningが許可されている場合
    if (have_major_receiver || profile.morphism() == 1 ||
        (profile.morphism() == 2 && UseBimorphicInlining)) { <-- receiverが2の場合
      // receiver_method = profile.method();
      // Profiles do not suggest methods now.  Look it up in the major receiver.
      receiver_method = call_method->resolve_invoke(jvms->method()->holder(),
                                                    profile.receiver(0)); <-- ここはreceiver0
    }
    if (receiver_method != NULL) {
      // The single majority receiver sufficiently outweighs the minority.
      CallGenerator* hit_cg = this->call_generator(receiver_method,
                                                   vtable_index, !call_is_virtual, jvms, allow_inline, prof_factor);
      if (hit_cg != NULL) {
        // Look up second receiver.
        CallGenerator* next_hit_cg = NULL;
        ciMethod* next_receiver_method = NULL;
        if (profile.morphism() == 2 && UseBimorphicInlining) {
          next_receiver_method = call_method->resolve_invoke(jvms->method()->holder(),
                                                             profile.receiver(1));
          if (next_receiver_method != NULL) {
            next_hit_cg = this->call_generator(next_receiver_method,
                                               vtable_index, !call_is_virtual, jvms,
                                               allow_inline, prof_factor);
            if (next_hit_cg != NULL && !next_hit_cg->is_inline() &&
                have_major_receiver && UseOnlyInlinedBimorphic) {
              // Skip if we can't inline second receiver's method
              next_hit_cg = NULL;
            }
          }
        }
        CallGenerator* miss_cg;
        Deoptimization::DeoptReason reason = (profile.morphism() == 2) ?
          Deoptimization::Reason_bimorphic :
          Deoptimization::Reason_class_check;

        if (( profile.morphism() == 1 ||
              (profile.morphism() == 2 && next_hit_cg != NULL) ) &&
            // too_many_traps()の場合、脱仮想化によるパフォーマンス低下が懸念されるため、抑止
            !too_many_traps(jvms->method(), jvms->bci(), reason)
           ) {
          // Generate uncommon trap for class check failure path
          // in case of monomorphic or bimorphic virtual call site.

          // C2コンパイラの脱仮想化の保証は、uncommon_trap()の埋め込みで行うっぽい
          // Deoptimizationへのtrap bimorphic or class_check
          miss_cg = CallGenerator::for_uncommon_trap(call_method, reason,
                                                     Deoptimization::Action_maybe_recompile);
        } else {
          // Generate virtual call for class check failure path
          // in case of polymorphic virtual call site.
          miss_cg = CallGenerator::for_virtual_call(call_method, vtable_index);
        }
        // 第1候補(receiver0)と第2候補(receiver1)をpredicated_callで脱仮想化
        // miss_cg     <-- 脱最適化
        // hit_cg      <-- 第1候補
        // next_hit_cg <-- 第2候補
        if (miss_cg != NULL) {
          if (next_hit_cg != NULL) {
            NOT_PRODUCT(trace_type_profile(jvms->method(), jvms->depth(), jvms->bci(), next_receiver_method, profile.receiver(1), si
            // We don't need to record dependency on a receiver here and below.
            // Whenever we inline, the dependency is added by Parse::Parse().
            miss_cg = CallGenerator::for_predicted_call(profile.receiver(1),
                miss_cg,
                next_hit_cg,
                PROB_MAX);
          }
          if (miss_cg != NULL) {
            NOT_PRODUCT(trace_type_profile(jvms->method(), jvms->depth(), jvms->bci(), receiver_method, profile.receiver(0), site_co
            cg = CallGenerator::for_predicted_call(profile.receiver(0),
                 miss_cg,
                 hit_cg,
                 profile.receiver_prob(0));
            if (cg != NULL)  return cg;
          }
        }
      }
    }
  }

  CallGenerator* CallGenerator::for_predicted_call(ciKlass* predicted_receiver,
                                                   CallGenerator* if_missed,
                                                   CallGenerator* if_hit,
                                                   float hit_prob);

  if (nullcheck && typecheck(第1候補)) {
    //第1候補
  } else
    if (nullcheck && typecheck(第2候補)) {
      //第2候補
    } else {
      //脱最適化
    }
  }


CallGenerator::for_predicated_call()
-------------------------------------------------------------------------------

以下の順番で挿入っぽい

null_check

type_check

diamond if_missed

diamond if_hit

can_be_statically_bound()
-------------------------------------------------------------------------------

Parse::optimize_inlining() ::

  // If it is obviously final, do not bother to call find_monomorphic_target,
  // because the class hierarchy checks are not needed, and may fail due to
  // incompletely loaded classes.  Since we do our own class loading checks
  // in this module, we may confidently bind to any method.
  if (dest_method->can_be_statically_bound()) {
    return dest_method;
  }

  bool methodOopDesc::can_be_statically_bound() const {
    if (is_final_method())  return true;
    return vtable_index() == nonvirtual_vtable_index;
  }

疑問点
===============================================================================



おまけ
===============================================================================

実はSharkでもresolve_invoke()と、find_monomorphic_target()を呼び出し、Devirtualizeを試行する

ちゃんとdependencyに登録もする

ただし、非常に簡易な解析しかしないので、C1より抑え気味


ex) shark/sharkTopLevelBlock.cpp improve_virtual_call()

code ::

  SharkTopLevelBlock::do_call()
    ciMethod *optimized_method = improve_virtual_call()
    if (optimized_method) {
      call_method = optimized_method;
      call_is_virtual = false;
    }
    SharkInliner::attempt_inline(call_method, current_state())

どうやら、entry_pointや、deoptimize時のguard文も随所に挿入


C2のDevirtualization
===============================================================================

C2は、Guarded Devirtualizationを行う。

基本的に、プロファイル結果のみ参照して、Porymorphic callの種類をカウントする。

receiver==1 mono-morhpic call
receiver==2 bi-morphic call
receiver>=3 mega-morphic call


