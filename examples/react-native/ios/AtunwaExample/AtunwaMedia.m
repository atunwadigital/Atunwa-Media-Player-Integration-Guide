#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(AtunwaMedia, RCTEventEmitter)

RCT_EXTERN_METHOD(setup:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(initializePlayer:(NSString *)apiKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(activeStation:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(load:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(launchPlayer:(NSString *)apiKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(openPlayer:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(initializePublisher:(NSString *)sdkKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(refreshPublisher:(NSString *)sdkKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(loadStation:(NSString *)assetKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(activateStation:(NSString *)assetKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(play:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(pause:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(reset:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end
