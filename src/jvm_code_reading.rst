JVMCodeReading
###############################################################################

nminoru_jp
*******************************************************************************

===============================================================================

inflation
deflation


revocation


-------------------------------------------------------------------------------

===============================================================================
void ObjectSynchronizer::slow_enter(Handle obj, BasicLock* lock, TRAPS) {
markOop mark = obj->mark();
assert(!mark->has_bias_pattern(), "should not see bias pattern here");

if (mark->is_neutral()) {
// Anticipate successful CAS -- the ST of the displaced mark must
// be visible <= the ST performed by the CAS.
lock->set_displaced_header(mark);
if (mark == (markOop) Atomic::cmpxchg_ptr(lock, obj()->mark_addr(), mark)) {
TEVENT (slow_enter: release stacklock) ;
return ;
}
// Fall through to inflate() ...
} else
if (mark->has_locker() && THREAD->is_lock_owned((address)mark->locker())) {
assert(lock != mark->locker(), "must not re-lock the same lock");
assert(lock != (BasicLock*)obj->mark(), "don't relock with same BasicLock");
lock->set_displaced_header(NULL);
return;
}



      
ObjectMonitor * ATTR ObjectSynchronizer::inflate (Thread * Self, oop object) {

Biased Locking
===============================================================================

biased モードには戻らない。
冗長な2度目のcasは使用しない。

pen4だと、100cycleくらいだけど、 (net burst)
現代のcasは10cycleくらいなんじゃないの

cas キャッシュラインに対して発行するため、

バスロックがなくなるのがうれしいのか

specjbbは、synchronizedが多い



===============================================================================
===============================================================================

-------------------------------------------------------------------------------
-------------------------------------------------------------------------------


-------------------------------------------------------------------------------

.. image:: xxx.png

.. literalinclude:: xxx.js

.. graphviz::


xxx ::

  xxx

.. code-block:: python
    :linenos:

.. literalinclude:: src/test.py

  :language: python
  :linenos:


