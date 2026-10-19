#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
/// Native counterpart of Android GridLocator/QuadDetector/CellGrid. Input/output RGB, top row first.
@interface SudokuGridGeometry : NSObject
+ (nullable NSDictionary<NSString *, id> *)locateRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height;
+ (nullable NSArray<NSArray<NSNumber *> *> *)frameRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height;
+ (nullable NSDictionary<NSString *, id> *)framingRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height;
+ (NSDictionary<NSString *, NSData *> *)diagnosticStagesRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height;
+ (nullable NSDictionary<NSString *, id> *)decodePhoto:(NSData *)data orientation:(NSInteger)orientation sample:(NSInteger)sample;
+ (nullable NSDictionary<NSString *, id> *)locateRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height ruleAligner:(NSArray<NSArray<NSNumber *> *> *(^ _Nullable)(NSArray<NSArray<NSNumber *> *> *corners))ruleAligner;
@end
NS_ASSUME_NONNULL_END
