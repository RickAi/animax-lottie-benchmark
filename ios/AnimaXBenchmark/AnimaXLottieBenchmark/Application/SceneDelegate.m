// Copyright 2025 The Lynx Authors. All rights reserved.
// Licensed under the Apache License Version 2.0 that can be found in the
// LICENSE file in the root directory of this source tree.

#import "SceneDelegate.h"
#import <UIKit/UIKit.h>

@interface SceneDelegate ()

@end

@implementation SceneDelegate

- (void)scene:(UIScene *)scene
    willConnectToSession:(UISceneSession *)session
                 options:(UISceneConnectionOptions *)connectionOptions {
  if ([scene isKindOfClass:[UIWindowScene class]]) {
    UIWindowScene *windowScene = (UIWindowScene *)scene;
    self.window = [[UIWindow alloc] initWithWindowScene:windowScene];

    Class benchmarkClass = NSClassFromString(@"BenchmarkViewController");
    UIViewController *rootViewController =
        [benchmarkClass isSubclassOfClass:[UIViewController class]]
            ? [[benchmarkClass alloc] init]
            : [[UIViewController alloc] init];
    UINavigationController *navigationController =
        [[UINavigationController alloc] initWithRootViewController:rootViewController];

    self.window.rootViewController = navigationController;
    [self.window makeKeyAndVisible];
  }
}

@end
