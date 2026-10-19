#include "Geometry.hpp"
#include <opencv2/imgcodecs.hpp>
#import "SudokuGridGeometry.h"
@implementation SudokuGridGeometry
+ (NSDictionary<NSString *,id> *)locateRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height {
    return [self locateRGB:rgb width:width height:height ruleAligner:nil];
}
+ (NSDictionary<NSString *,id> *)locateRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height ruleAligner:(NSArray<NSArray<NSNumber *> *> *(^)(NSArray<NSArray<NSNumber *> *> *))ruleAligner {
    if(width<32||height<32||width>12000||height>12000||(NSUInteger)(width*height*3)!=rgb.length)return nil;
    try {
        cv::Mat source((int)height,(int)width,CV_8UC3,const_cast<void *>(rgb.bytes));
        std::function<sudoku::Quad(const sudoku::Quad&)> align;
        if(ruleAligner)align=[&](const sudoku::Quad& quad){
            NSMutableArray *corners=[NSMutableArray array];for(auto p:quad)[corners addObject:@[@(p.x),@(p.y)]];
            NSArray *result=ruleAligner(corners);if(result.count!=4)return quad;
            sudoku::Quad out;for(int i=0;i<4;i++)out[i]=cv::Point2d([result[i][0] doubleValue],[result[i][1] doubleValue]);return out;
        };
        auto located=sudoku::locate(source,align);if(located.rgb.empty())return nil;
        NSData *pixels=[NSData dataWithBytes:located.rgb.data length:located.rgb.total()*3];
        cv::Mat gray=located.curved?sudoku::photoLuma(located.rgb):sudoku::rectify(sudoku::photoLuma(source),located.quad);
        NSMutableArray *corners=[NSMutableArray array];for(auto p:located.quad)[corners addObject:@[@(p.x),@(p.y)]];
        NSMutableArray *surface=[NSMutableArray array];if(!located.surface.empty())for(int t=0;t<15;t++)for(int c=0;c<2;c++)[surface addObject:@(located.surface.at<double>(t,c))];
        return @{ @"surface":surface,@"rgb":pixels,@"gray":[NSData dataWithBytes:gray.data length:gray.total()],@"corners":corners,
            @"side":@(sudoku::Side),@"curved":@(located.curved),@"score":@(located.strength) };
    } catch(const cv::Exception&) { return nil; } catch(const std::exception&) { return nil; }
}
+ (NSArray<NSArray<NSNumber *> *> *)frameRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height {
    NSDictionary *frame = [self framingRGB:rgb width:width height:height];
    return [frame[@"accepted"] boolValue] ? frame[@"corners"] : nil;
}
+ (NSDictionary<NSString *,id> *)framingRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height {
    if(width<32||height<32||width>1600||height>1600||(NSUInteger)(width*height*3)!=rgb.length)return nil;
    try {
        cv::Mat source((int)height,(int)width,CV_8UC3,const_cast<void *>(rgb.bytes)),gray;
        gray=sudoku::photoLuma(source);std::vector<sudoku::Candidate> candidates;
        for(auto q:sudoku::detect(gray,720)){double score=sudoku::score(sudoku::rectify(gray,q,sudoku::scoringSize(q)));candidates.push_back({q,score});}
        sudoku::Candidate best;bool accepted=sudoku::winner(candidates,best);
        if(!accepted&&!candidates.empty())best=*std::max_element(candidates.begin(),candidates.end(),[](auto&a,auto&b){return a.strength<b.strength;});
        cv::Mat grid;
        if(!accepted){auto fallback=sudoku::locate(source);if(!fallback.rgb.empty()){accepted=true;best={fallback.quad,fallback.strength};grid=fallback.curved?sudoku::photoLuma(fallback.rgb):sudoku::rectify(gray,fallback.quad);}}
        NSMutableArray *corners=[NSMutableArray array];
        if(accepted||!candidates.empty())for(auto p:best.quad)[corners addObject:@[@(p.x/width),@(p.y/height)]];
        if(accepted&&grid.empty())grid=sudoku::rectify(gray,best.quad,1152);
        return @{ @"accepted":@(accepted),@"corners":corners,@"candidates":@(candidates.size()),
            @"nearGrid":@(!candidates.empty()&&best.strength>=.35*.6),
            @"luminance":[NSData dataWithBytes:gray.data length:gray.total()],
            @"gridSide":@(grid.cols),@"gridLuminance":grid.empty()?[NSData data]:[NSData dataWithBytes:grid.data length:grid.total()] };
    } catch(const cv::Exception&) { return nil; } catch(const std::exception&) { return nil; }
}
+ (NSDictionary<NSString *,NSData *> *)diagnosticStagesRGB:(NSData *)rgb width:(NSInteger)width height:(NSInteger)height {
    NSMutableDictionary *result=[NSMutableDictionary dictionary];
    if(width<1||height<1||rgb.length!=(NSUInteger)(width*height*3))return result;
    cv::Mat source((int)height,(int)width,CV_8UC3,const_cast<void *>(rgb.bytes));auto full=sudoku::photoLuma(source);
    for(int edge:{1000,1600}){
        double scale=(double)edge/std::max(width,height);cv::Mat small,blur,binary,closed;
        cv::resize(full,small,cv::Size((int)(width*scale),(int)(height*scale)),0,0,cv::INTER_LINEAR_EXACT);
        cv::GaussianBlur(small,blur,cv::Size(5,5),0);
        cv::adaptiveThreshold(blur,binary,255,cv::ADAPTIVE_THRESH_MEAN_C,cv::THRESH_BINARY_INV,31,7);
        cv::morphologyEx(binary,closed,cv::MORPH_CLOSE,cv::getStructuringElement(cv::MORPH_RECT,cv::Size(3,3)));
        for(auto item:std::vector<std::pair<std::string,cv::Mat>>{{"small",small},{"blur",blur},{"binary",binary},{"closed",closed}})
            result[[NSString stringWithFormat:@"native-%d-%s.gray",edge,item.first.c_str()]]=[NSData dataWithBytes:item.second.data length:item.second.total()];
    }
    return result;
}
+ (NSDictionary<NSString *,id> *)decodePhoto:(NSData *)data orientation:(NSInteger)orientation sample:(NSInteger)sample {
    if(data.length==0||sample<1)return nil;
    try {
        cv::Mat encoded(1,(int)data.length,CV_8UC1,const_cast<void *>(data.bytes));
        int reduced=sample>=8?cv::IMREAD_REDUCED_COLOR_8:sample>=4?cv::IMREAD_REDUCED_COLOR_4:sample>=2?cv::IMREAD_REDUCED_COLOR_2:cv::IMREAD_COLOR;
        cv::Mat pixels=cv::imdecode(encoded,reduced|cv::IMREAD_IGNORE_ORIENTATION);if(pixels.empty())return nil;
        cv::cvtColor(pixels,pixels,cv::COLOR_BGR2RGB);
        if(sample>8)cv::resize(pixels,pixels,cv::Size((int)((pixels.cols*8+sample-1)/sample),(int)((pixels.rows*8+sample-1)/sample)));
        switch(orientation){
            case 2:cv::flip(pixels,pixels,1);break;
            case 3:cv::rotate(pixels,pixels,cv::ROTATE_180);break;
            case 4:cv::flip(pixels,pixels,0);break;
            case 5:cv::transpose(pixels,pixels);break;
            case 6:cv::rotate(pixels,pixels,cv::ROTATE_90_CLOCKWISE);break;
            case 7:cv::transpose(pixels,pixels);cv::flip(pixels,pixels,-1);break;
            case 8:cv::rotate(pixels,pixels,cv::ROTATE_90_COUNTERCLOCKWISE);break;
        }
        return @{@"rgb":[NSData dataWithBytes:pixels.data length:pixels.total()*3],@"width":@(pixels.cols),@"height":@(pixels.rows)};
    }catch(const cv::Exception&){return nil;}
}
@end
