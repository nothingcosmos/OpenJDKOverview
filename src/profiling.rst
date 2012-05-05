Profiling
###############################################################################

プロファイル情報の活用方法
===============================================================================

HotSpotは、下記の情報のプロファイル情報を取得し、JIT時に活用する。

メソッドの実行回数のカウント
-------------------------------------------------------------------------------

--> JITコンパイルするかどうかの判定に利用

ループの実行回数のカウント
-------------------------------------------------------------------------------

--> JITコンパイルするかどうかの判定に利用

--> ループ最適化のパラメータの最適化に利用

仮想関数呼び出しの、種類の統計
-------------------------------------------------------------------------------

--> 脱仮想化に利用

calleeの引数の値の統計
-------------------------------------------------------------------------------

--> 関数の特殊化に利用

分岐の統計
-------------------------------------------------------------------------------

--> コード配置の最適化に利用 キャッシュや分岐予測を改善できる。

キャスト先の型の統計
-------------------------------------------------------------------------------

--> checkcastを省略できるかも

DynamicCastの統計
-------------------------------------------------------------------------------

--> instanceofを省略できるかも

Trapの統計
-------------------------------------------------------------------------------

--> ヤバい関数は最適化しませんし、インタプリタに戻します。



===============================================================================
-------------------------------------------------------------------------------
プロファイルを制御するオプション類

runtime/global.hpp

UseTypeProfile=true

TypeProfileMajorReceiverPercent=90

TypeProfileCasts=true

ProfileDynamicTypes=true

ProfileTraps=true

ProfileMaturityPercentage=20


TypeProfileWidth=2
  number of receiver types to record in call/cast profile

BciProfileWidth=2
  number of return bci's to record in ret profile

ProfilerNodeSize=1024


===============================================================================

-------------------------------------------------------------------------------
elise@elise-desktop:~/language/java/openjdk6/hotspot/src/share/vm$ grep UseTypeProfile */*
  opto/doCall.cpp:  if (call_is_virtual && UseTypeProfile && profile.has_receiver(0)) {
  opto/graphKit.cpp:  if (!UseTypeProfile || !TypeProfileCasts) return NULL;
  runtime/globals.hpp:define_pd_global(bool, UseTypeProfile,               false);
  runtime/globals.hpp:  product(bool, UseTypeProfile, true,                                       \

elise@elise-desktop:~/language/java/openjdk6/hotspot/src/share/vm$ grep TypeProfileCasts */*
  ci/ciMethod.cpp:// Also reports receiver types for non-call type checks (if TypeProfileCasts).
  oops/methodDataOop.cpp:    if (TypeProfileCasts) {
  oops/methodDataOop.cpp:    if (TypeProfileCasts) {
  opto/graphKit.cpp:  if (!UseTypeProfile || !TypeProfileCasts) return NULL;
  runtime/globals.hpp:  develop(bool, TypeProfileCasts,  true,                                    \

elise@elise-desktop:~/language/java/openjdk6/hotspot/src/share/vm$ grep ProfileDynamicTypes */*
  opto/graphKit.cpp:  bool never_see_null = (ProfileDynamicTypes  // aggressive use of profile
  opto/graphKit.cpp:  if (ProfileDynamicTypes && data != NULL) {
  opto/parse2.cpp:      if (ProfileDynamicTypes)
  runtime/globals.hpp:  diagnostic(bool, ProfileDynamicTypes, true,                               \




ProfileDynamicTypes


graphKit::gen_instanceof

  ProfileDynamicTypes seems_never_null()

  null_check_oop()
    uncommon_trapを挿入して

  maybe_cast_profiled_receiver()



