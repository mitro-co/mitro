#include "base/mac/mac_util.h"

#include "Foundation/Foundation.h"

namespace base {

void StartRunLoop() {
  CFRunLoopRun();
}

void StopRunLoop() {
  NSRunLoop* runLoop = [NSRunLoop currentRunLoop];
  CFRunLoopStop([runLoop getCFRunLoop]);
}

}  // namespace base
