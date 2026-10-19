#pragma once
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/calib3d.hpp>
#include <algorithm>
#include <numeric>
#include <map>
#include <set>
#include <array>
#include <cmath>
#include <functional>

// Port of core/vision's geometry, kept in its same outline -> rules -> lattice -> surface -> occlusion order.
// Thresholds and lattice fitting are shared in intent; corpus replay guards platform differences in OpenCV.
namespace sudoku {
using namespace cv;
using Quad = std::array<Point2d, 4>;
inline Mat perspective(const Quad& from,const Quad& to){
    std::array<Point2f,4> a,b;for(int i=0;i<4;i++){a[i]=from[i];b[i]=to[i];}
    return getPerspectiveTransform(a.data(),b.data());
}
constexpr int Side = 1152;
// Integer conversion used by Android Images.photograph, intentionally before warping.
inline Mat photoLuma(const Mat& rgb) {
    Mat gray(rgb.rows,rgb.cols,CV_8UC1);
    for(int y=0;y<rgb.rows;y++){const auto* src=rgb.ptr<uchar>(y);auto* dst=gray.ptr<uchar>(y);
        for(int x=0;x<rgb.cols;x++)dst[x]=(src[x*3]*299+src[x*3+1]*587+src[x*3+2]*114)/1000;}
    return gray;
}
inline double median(std::vector<double> v) { if(v.empty()) return 0; std::sort(v.begin(),v.end()); return v[v.size()/2]; }
template<typename P> inline Quad order(const std::vector<P>& input) {
    std::vector<Point2d> p;for(auto point:input)p.emplace_back(point.x,point.y);
    Quad q; q[0]=*std::min_element(p.begin(),p.end(),[](auto a,auto b){return a.x+a.y<b.x+b.y;});
    q[2]=*std::max_element(p.begin(),p.end(),[](auto a,auto b){return a.x+a.y<b.x+b.y;});
    q[1]=*std::max_element(p.begin(),p.end(),[](auto a,auto b){return a.x-a.y<b.x-b.y;});
    q[3]=*std::min_element(p.begin(),p.end(),[](auto a,auto b){return a.x-a.y<b.x-b.y;});
    bool distinct=true;for(int i=0;i<4;i++)for(int j=i+1;j<4;j++)if(q[i]==q[j])distinct=false;
    if(!distinct&&p.size()==4){auto clockwise=p;Point2d c;for(auto a:p)c+=a*.25;
        std::stable_sort(clockwise.begin(),clockwise.end(),[&](auto a,auto b){return std::atan2(a.y-c.y,a.x-c.x)<std::atan2(b.y-c.y,b.x-c.x);});
        int first=0;for(int i=1;i<4;i++)if(clockwise[i].x+clockwise[i].y<clockwise[first].x+clockwise[first].y)first=i;
        for(int i=0;i<4;i++)q[i]=clockwise[(first+i)%4];}return q;
}
inline double edgeRatio(const Quad& q) { double lo=1e30,hi=0;for(int i=0;i<4;i++){double d=norm(q[i]-q[(i+1)%4]);lo=std::min(lo,d);hi=std::max(hi,d);}return hi>0?lo/hi:0; }
inline double area(const Quad& q) { double sum=0;for(int i=0;i<4;i++)sum+=q[i].x*q[(i+1)%4].y-q[(i+1)%4].x*q[i].y;return std::abs(sum)/2; }
inline double scoringSize(const Quad& q) { double hi=0; for(int i=0;i<4;i++)hi=std::max(hi,norm(q[i]-q[(i+1)%4]));return std::clamp(hi,288.0,(double)Side); }
inline Mat rectify(const Mat& full,const Quad& q,double side=Side) {
    Quad to={Point2f(0,0),Point2f(side,0),Point2f(side,side),Point2f(0,side)};Mat out;
    warpPerspective(full,out,perspective(q,to),Size((int)side,(int)side));return out;
}
inline double axisScore(const std::vector<double>& p,int obscured=0) {
    int n=(int)p.size(),window=std::max(4,(int)(n/9.0*.18));double page=median(p);std::vector<double>s;
    for(int line=0;line<=9;line++){int centre=(int)(line*(n-1.0)/9.0);double peak=0;
        for(int at=std::max(0,centre-window);at<=std::min(n-1,centre+window);at++)peak=std::max(peak,p[at]);s.push_back(std::max(0.0,peak-page));}
    double typical=median(s);if(typical<n*.25)return 0;
    for(double& v:s)v=std::min(1.0,v/typical);std::sort(s.begin(),s.end());return s[std::clamp(obscured,0,9)];
}
inline double score(const Mat& gray,int obscured=0) {
    Mat binary;adaptiveThreshold(gray,binary,255,ADAPTIVE_THRESH_MEAN_C,THRESH_BINARY_INV,31,10);
    std::vector<double>cols(gray.cols),rows(gray.rows);
    for(int y=0;y<binary.rows;y++){auto p=binary.ptr<uchar>(y);for(int x=0;x<binary.cols;x++)if(p[x]){cols[x]++;rows[y]++;}}
    return std::min(axisScore(cols,obscured),axisScore(rows,obscured));
}
inline Quad toQuad(const std::vector<Point>& contour,double scale) {
    std::vector<Point>approx;approxPolyDP(contour,approx,.02*arcLength(contour,true),true);std::vector<Point2d>p;
    if(approx.size()==4)for(auto a:approx)p.emplace_back(a.x/scale,a.y/scale);
    else {Point2f box[4];minAreaRect(contour).points(box);for(auto a:box)p.emplace_back((double)a.x/scale,(double)a.y/scale);}return order(p);
}
inline std::vector<Quad> detect(const Mat& full,int edge,bool rules=false) {
    double scale=(double)edge/std::max(full.cols,full.rows);Mat small,blur,binary,closed;
    resize(full,small,Size(std::max(1,(int)(full.cols*scale)),std::max(1,(int)(full.rows*scale))),0,0,INTER_LINEAR_EXACT);
    if(rules)blur=small;else GaussianBlur(small,blur,Size(5,5),0);
    adaptiveThreshold(blur,binary,255,ADAPTIVE_THRESH_MEAN_C,THRESH_BINARY_INV,31,7);
    if(rules){Mat h,v;int len=std::max(15,(int)(std::min(full.cols,full.rows)*scale/12));
        morphologyEx(binary,h,MORPH_OPEN,getStructuringElement(MORPH_RECT,Size(len,1)));
        morphologyEx(binary,v,MORPH_OPEN,getStructuringElement(MORPH_RECT,Size(1,len)));bitwise_or(h,v,closed);
    }else morphologyEx(binary,closed,MORPH_CLOSE,getStructuringElement(MORPH_RECT,Size(3,3)));
    std::vector<std::vector<Point>>contours;findContours(closed,contours,RETR_LIST,CHAIN_APPROX_SIMPLE);
    std::stable_sort(contours.begin(),contours.end(),[](auto&a,auto&b){return contourArea(a)>contourArea(b);});std::vector<Quad> out;
    for(auto& c:contours)if(contourArea(c)>small.total()*.03){out.push_back(toQuad(c,scale));if(out.size()==10)break;}return out;
}
// The same global-grid fit used by Kotlin GridLineFitter, for rule-outline alignment.
inline std::vector<double> fitAxis(const std::vector<double>& profile) {
    int n=(int)profile.size();double nominal=(n-1.0)/9, start=0,pitch=nominal,best=0;
    for(int line=0;line<=9;line++)best+=profile[(int)(line*nominal)];best*=1.2;
    for(double offset=-nominal*.6;offset<=nominal*.6;offset+=nominal*.02)
        for(double step=nominal*.88;step<=nominal*1.12;step+=nominal*.004)if(offset+9*step<=n-1+nominal*.6){
            double value=0;for(int line=0;line<=9;line++){int at=(int)(offset+line*step);if(at>=0&&at<n)value+=profile[at];}
            if(value>best){best=value;start=offset;pitch=step;}}
    double strongest=*std::max_element(profile.begin(),profile.end());if(strongest<=0)return {};
    int window=std::max(4,(int)(n/9.0*.18)),assumed=0;std::vector<double>lines;
    for(int line=0;line<=9;line++){double place=start+line*pitch;int centre=std::clamp((int)place,0,n-1),from=std::max(0,centre-window),to=std::min(n-1,centre+window);
        double peak=0;for(int at=from;at<=to;at++)peak=std::max(peak,profile[at]);
        if(peak<strongest*.2){assumed++;lines.push_back(place);continue;}
        double weighted=0,weight=0;for(int at=from;at<=to;at++)if(profile[at]>=peak*.6){weighted+=at*profile[at];weight+=profile[at];}
        if(weight<=0){assumed++;lines.push_back(place);}else lines.push_back(weighted/weight);
    }
    if(assumed>2)return {};for(int i=1;i<10;i++)if(lines[i]-lines[i-1]<n/9.0*.4)return {};return lines;
}
inline Quad alignRules(const Mat& gray,const Quad& q) {
    double extent=scoringSize(q);Mat image=rectify(gray,q,extent),binary;int side=image.cols;
    adaptiveThreshold(image,binary,255,ADAPTIVE_THRESH_MEAN_C,THRESH_BINARY_INV,31,10);
    std::vector<double>cols(side),rows(side);for(int y=0;y<side;y++){auto p=binary.ptr<uchar>(y);for(int x=0;x<side;x++)if(p[x]){cols[x]++;rows[y]++;}}
    auto x=fitAxis(cols),y=fitAxis(rows);if(x.empty()||y.empty())return q;
    Quad source={Point2f(0,0),Point2f(extent,0),Point2f(extent,extent),Point2f(0,extent)};
    std::vector<Point2f>rules={Point2f(x.front(),y.front()),Point2f(x.back(),y.front()),Point2f(x.back(),y.back()),Point2f(x.front(),y.back())},mapped;
    perspectiveTransform(rules,mapped,perspective(source,q));return order(mapped);
}
inline Quad bounds(const Quad& q) {double l=q[0].x,r=l,t=q[0].y,b=t;for(auto p:q){l=std::min(l,p.x);r=std::max(r,p.x);t=std::min(t,p.y);b=std::max(b,p.y);}return {Point2d(l,t),Point2d(r,t),Point2d(r,b),Point2d(l,b)};}
inline Quad grown(const Quad& q,double growth){Point2d c;for(auto p:q)c+=p*.25;Quad out;for(int i=0;i<4;i++)out[i]=c+(q[i]-c)*growth;return out;}
struct Candidate {Quad quad;double strength;};
inline bool winner(const std::vector<Candidate>& options,Candidate& out) {
    double best=0;const Candidate* strongest=nullptr;
    for(auto& c:options)if(c.strength>=.35&&c.strength>best){best=c.strength;strongest=&c;}if(!strongest)return false;
    double largest=-1;for(auto& c:options)if(c.strength>=.35&&c.strength>=best*.75&&edgeRatio(c.quad)>=edgeRatio(strongest->quad)*.9&&area(c.quad)>largest){out=c;largest=area(c.quad);}return largest>=0;
}
struct Cell {Point2f centre;double span;std::array<Point2f,4> corners;};
struct Lattice {Quad quad;Mat curve;};
inline double power(double value,int exponent){double result=1;for(int i=0;i<exponent;i++)result*=value;return result;}
inline void terms(double x,double y,double* into){int t=0;for(int total=0;total<=4;total++)for(int i=0;i<=total;i++)into[t++]=power(x,i)*power(y,total-i);}
inline std::vector<int> claim(const std::vector<Point2d>& points) {
    struct Claim{double away;int cell,place;};std::vector<Claim>claims;
    for(int i=0;i<(int)points.size();i++)for(int r=0;r<9;r++)for(int c=0;c<9;c++){double d=norm(points[i]-Point2d(c,r));if(d<=.7)claims.push_back({d,i,r*9+c});}
    std::stable_sort(claims.begin(),claims.end(),[](auto&a,auto&b){return a.away<b.away;});std::vector<int>places(points.size(),-1);std::set<int>taken;
    for(auto& c:claims)if(places[c.cell]<0&&!taken.count(c.place)){places[c.cell]=c.place;taken.insert(c.place);}return places;
}
inline bool fitLattice(const std::vector<Cell>& cells,double scale,Lattice& out) {
    std::vector<Point2f>centres;for(auto c:cells)centres.push_back(c.centre);
    Point2f box[4];minAreaRect(centres).points(box);
    auto unit=[](Point2f a,Point2f b){Point2d d((double)b.x-a.x,(double)b.y-a.y);return d/std::max(1e-6,norm(d));};
    Point2d first=unit(box[0],box[1]),second=unit(box[1],box[2]);Point2d across=std::abs(first.y)<=std::abs(second.y)?first:second,down=std::abs(first.y)<=std::abs(second.y)?second:first;
    // Same ordering as Android's minAreaRect lattice; signs are resolved by projected extremes.
    std::vector<double>xs,ys;for(auto p:centres){xs.push_back(p.x*across.x+p.y*across.y);ys.push_back(p.x*down.x+p.y*down.y);}
    double x0=*std::min_element(xs.begin(),xs.end()),x1=*std::max_element(xs.begin(),xs.end()),y0=*std::min_element(ys.begin(),ys.end()),y1=*std::max_element(ys.begin(),ys.end());
    std::vector<Point2d>guesses;for(int i=0;i<(int)centres.size();i++)guesses.emplace_back((xs[i]-x0)/std::max(1e-6,x1-x0)*8,(ys[i]-y0)/std::max(1e-6,y1-y0)*8);
    auto places=claim(guesses);Mat fit;
    for(int pass=0;pass<3;pass++){
        std::vector<Point2f>source,target;for(int i=0;i<(int)places.size();i++)if(places[i]>=0){source.emplace_back(places[i]%9,places[i]/9);target.push_back(centres[i]);}
        if(source.size()<20)continue;Mat attempt=findHomography(source,target);if(attempt.empty())continue;fit=attempt;
        std::vector<Point2f> read;perspectiveTransform(centres,read,fit.inv());guesses.clear();for(auto p:read)guesses.emplace_back(p.x,p.y);places=claim(guesses);
    }
    if(fit.empty())return false;
    std::vector<Point2f>edge={Point2f(-.5,-.5),Point2f(8.5,-.5),Point2f(8.5,8.5),Point2f(-.5,8.5)},onPhoto;
    perspectiveTransform(edge,onPhoto,fit);std::vector<Point2d> fullCorners;for(auto p:onPhoto)fullCorners.emplace_back(p.x/scale,p.y/scale);out.quad=order(fullCorners);
    std::vector<double>gaps,spans;for(int i=0;i<(int)centres.size();i++){double nearest=1e30;for(int j=0;j<(int)centres.size();j++)if(i!=j)nearest=std::min(nearest,norm(centres[i]-centres[j]));gaps.push_back(nearest);spans.push_back(cells[i].span);}
    double pitch=median(gaps),inset=std::clamp((pitch-median(spans))/(2*pitch),0.0,.2);
    std::vector<std::array<double,15>>rows;std::vector<Point2d>targets;std::set<int>taken;
    for(int i=0;i<(int)cells.size();i++)if(places[i]>=0&&!taken.count(places[i])){
        taken.insert(places[i]);int c=places[i]%9,r=places[i]/9;auto cell=cells[i];Point2d middle;for(auto p:cell.corners)middle+=Point2d(p.x,p.y)*.25;
        for(int k=0;k<5;k++){Point2f p=k<4?cell.corners[k]:cell.centre;
            double dx=k==4?0:p.x<middle.x?inset-.5:.5-inset,dy=k==4?0:p.y<middle.y?inset-.5:.5-inset;
            std::array<double,15>row;terms(c+dx,r+dy,row.data());rows.push_back(row);targets.emplace_back(p.x/scale,p.y/scale);}
    }
    if(taken.size()>=40){Mat a((int)rows.size(),15,CV_64F),b((int)rows.size(),2,CV_64F);
        for(int i=0;i<a.rows;i++){for(int t=0;t<15;t++)a.at<double>(i,t)=rows[i][t];b.at<double>(i,0)=targets[i].x;b.at<double>(i,1)=targets[i].y;}
        solve(a,b,out.curve,DECOMP_QR|DECOMP_NORMAL);}
    return true;
}
inline std::vector<Lattice> lattices(const Mat& full,int edge) {
    double scale=(double)edge/std::max(full.cols,full.rows);Mat small,blur,binary,closed;
    resize(full,small,Size(std::max(1,(int)(full.cols*scale)),std::max(1,(int)(full.rows*scale))),0,0,INTER_LINEAR_EXACT);GaussianBlur(small,blur,Size(5,5),0);
    adaptiveThreshold(blur,binary,255,ADAPTIVE_THRESH_MEAN_C,THRESH_BINARY_INV,31,7);morphologyEx(binary,closed,MORPH_CLOSE,getStructuringElement(MORPH_RECT,Size(3,3)));
    std::vector<std::vector<Point>>contours;std::vector<Vec4i>hierarchy;findContours(closed,contours,hierarchy,RETR_CCOMP,CHAIN_APPROX_SIMPLE);
    std::vector<int>holes;std::vector<double>areas;for(int i=0;i<(int)contours.size();i++)if(hierarchy[i][3]>=0&&contourArea(contours[i])>small.total()*.0005){holes.push_back(i);areas.push_back(contourArea(contours[i]));}
    std::vector<Lattice>out;if(holes.size()<30)return out;double typical=median(areas);std::vector<Cell>cells;
    for(int i:holes){double a=contourArea(contours[i]);if(a<typical*.5||a>typical*2.0)continue;Rect r=boundingRect(contours[i]);RotatedRect box=minAreaRect(contours[i]);Cell c;c.centre=Point2f(r.x+r.width/2.0,r.y+r.height/2.0);c.span=((double)box.size.width+box.size.height)/2;box.points(c.corners.data());cells.push_back(c);}
    std::vector<int>parent(cells.size());std::iota(parent.begin(),parent.end(),0);auto root=[&](int i){while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i];}return i;};
    double reach=std::sqrt(typical)*1.6;for(int i=0;i<(int)cells.size();i++)for(int j=i+1;j<(int)cells.size();j++)if(norm(cells[i].centre-cells[j].centre)<=reach)parent[root(i)]=root(j);
    std::vector<std::vector<Cell>> groups;std::map<int,int> groupIndex;for(int i=0;i<(int)cells.size();i++){int key=root(i);if(!groupIndex.count(key)){groupIndex[key]=(int)groups.size();groups.emplace_back();}groups[groupIndex[key]].push_back(cells[i]);}
    for(auto& group:groups)if(group.size()>=30){Lattice l;if(fitLattice(group,scale,l))out.push_back(l);}return out;
}
inline Mat flatten(const Mat& full,const Mat& curve,double extent) {
    int side=(int)extent;
    Mat mx(side,side,CV_32F),my(side,side,CV_32F),out;double term[15];
    for(int r=0;r<side;r++)for(int c=0;c<side;c++){terms(c/extent*9-.5,r/extent*9-.5,term);double x=0,y=0;for(int t=0;t<15;t++){x+=term[t]*curve.at<double>(t,0);y+=term[t]*curve.at<double>(t,1);}mx.at<float>(r,c)=x;my.at<float>(r,c)=y;}
    remap(full,out,mx,my,INTER_LINEAR,BORDER_REPLICATE);return out;
}
struct Located {Mat rgb;Quad quad;double strength=0;bool curved=false;Mat surface;};
inline Located locate(const Mat& rgb,const std::function<Quad(const Quad&)>& ruleAligner={}) {
    Mat gray=photoLuma(rgb);Located out;std::vector<Candidate>all;
    for(int edge:{1000,1600}){
        std::vector<Candidate>scored;for(auto q:detect(gray,edge)){Candidate c{q,score(rectify(gray,q,scoringSize(q)))};scored.push_back(c);all.push_back(c);}
        Candidate best;if(winner(scored,best)){out={rectify(rgb,best.quad),best.quad,best.strength,false};return out;}
        std::stable_sort(scored.begin(),scored.end(),[](auto&a,auto&b){return a.strength>b.strength;});Candidate rescue;double top=-1;
        for(int i=0;i<std::min(3,(int)scored.size());i++)for(double growth:{1.02,1.04}){auto q=grown(scored[i].quad,growth);double s=score(rectify(gray,q,scoringSize(q)));if(s>top){top=s;rescue={q,s};}}
        if(top>=.35)return {rectify(rgb,rescue.quad),rescue.quad,rescue.strength,false};
    }
    // Long-rule outlines before the more expensive cell lattice.
    for(int edge:{1000,1600}){
        auto candidates=detect(gray,edge),rules=detect(gray,edge,true);candidates.insert(candidates.end(),rules.begin(),rules.end());
        auto outlined=candidates;for(auto q:outlined)if(std::abs(std::atan2(q[1].y-q[0].y,q[1].x-q[0].x)*180/CV_PI)<10)candidates.push_back(bounds(q));
        Candidate best;double top=-1;for(auto q:candidates){q=ruleAligner?ruleAligner(q):alignRules(gray,q);double strength=score(rectify(gray,q,scoringSize(q)));if(strength>top){top=strength;best={q,strength};}}
        if(top>=.35)return {rectify(rgb,best.quad),best.quad,best.strength,false};
    }
    std::vector<Lattice>allLattices;
    for(int edge:{1000,1600}){auto found=lattices(gray,edge);allLattices.insert(allLattices.end(),found.begin(),found.end());
        for(auto& l:found){double s=score(rectify(gray,l.quad,scoringSize(l.quad)));if(s>=.35)return {rectify(rgb,l.quad),l.quad,s,false};}
        for(auto& l:found)if(!l.curve.empty()){double s=score(flatten(gray,l.curve,scoringSize(l.quad)));if(s>=.35){Mat flat=flatten(gray,l.curve,Side),color;cvtColor(flat,color,COLOR_GRAY2RGB);return {color,l.quad,s,true,l.curve};}}
    }
    // Android returns the best acceptable candidate at the first working resolution,
    // checking all outline resolutions before any cell-lattice resolution.
    for(int edge:{1000,1600}){
        Candidate best;double top=-1;
        for(auto q:detect(gray,edge)){double value=score(rectify(gray,q,scoringSize(q)),1);if(value>top){top=value;best={q,value};}}
        if(top>=.35)return {rectify(rgb,best.quad),best.quad,best.strength,false};
    }
    for(int edge:{1000,1600}){
        Candidate best;double top=-1;
        for(auto& l:lattices(gray,edge)){double value=score(rectify(gray,l.quad,scoringSize(l.quad)),1);if(value>top){top=value;best={l.quad,value};}}
        if(top>=.35)return {rectify(rgb,best.quad),best.quad,best.strength,false};
    }
    return out;
}
}
