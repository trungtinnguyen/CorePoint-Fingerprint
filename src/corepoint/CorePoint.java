/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package corepoint;

import corepoint.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.bytedeco.javacpp.indexer.DoubleRawIndexer;
import org.bytedeco.javacpp.indexer.FloatRawIndexer;
import org.bytedeco.javacpp.indexer.UByteRawIndexer;
import org.bytedeco.javacpp.opencv_core;
import static org.bytedeco.javacpp.opencv_core.BORDER_DEFAULT;
import static org.bytedeco.javacpp.opencv_core.CV_32F;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_imgcodecs;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;
import static org.bytedeco.javacpp.opencv_imgcodecs.imwrite;
import org.bytedeco.javacpp.opencv_imgproc;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.GaussianBlur;
import static org.bytedeco.javacpp.opencv_imgproc.resize;

/**
 *
 * @author nguyentrungtin
 */
public class CorePoint {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        File folder = new File("");
        String fileName = folder.getAbsolutePath() + "/src/finger/";
        File[] listOfFiles = new File(fileName).listFiles();
        Arrays.sort(listOfFiles);
        for(int i = 0; i < listOfFiles.length; i++){
            if (listOfFiles[i].getName().contains(".tif")){
                //opencv_core.Mat img = imread ("src/corepoint/2_4.tif", opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
                String name =  listOfFiles[i].getName();
                opencv_core.Mat img = imread (fileName + name, opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
                resize(img, img, new Size(120,120));
                //Mat seg = segmentImage(img);
                Mat normailizedImg = normalizeSubWindow(img);
                CorePoint cr = new CorePoint();
                Mat[] ori = cr.localSmooth(normailizedImg);
                Mat smooth = cr.smoothedOrientation(ori[0], ori[1]);
                cr.poinCare(smooth, img, listOfFiles[i].getName());
                //cr.euclidean();
            }
        }   
    }
   
    public static opencv_core.Mat normalizeSubWindow(opencv_core.Mat image){
        int ut = 139;
        int vt = 100;//1607;  
        Mat img = image.clone();
        double u = meanMatrix(img);
        double v = variance(img, u);
        UByteRawIndexer idx = img.createIndexer();
        for(int i = 0; i < img.rows(); i++){
            for(int j = 0; j < img.cols() ; j++){
                double beta = Math.sqrt((vt * 1.0 / v ) * (Math.pow(idx.get(i, j) - u, 2)));
                if(idx.get(i, j) > ut){
                    idx.put(i, j, 0, (int)ut + (int)beta);
                }
                else idx.put(i, j, 0, Math.abs((int)ut - (int)beta));      
            }
        }
        return img;
    }
   
    public static double variance(opencv_core.Mat image, double mean){
        double var = 0; 
        UByteRawIndexer idx = image.createIndexer();
        for(int i = 0; i < image.rows(); i++){
            for(int j = 0; j < image.cols(); j++){
                var += Math.pow((idx.get(i, j) - mean), 2);
            }
        }
        var /= (image.cols() * image.rows());
        return var;
    }
    
    public static double meanMatrix(opencv_core.Mat img){
        double sum = 0;
        UByteRawIndexer idx = img.createIndexer();
        for(int i = 0; i < img.rows(); i++){
            for(int j = 0; j < img.cols(); j++){
                sum += idx.get(i, j, 0);
            }
        }
        sum /= (img.cols() * img.rows());
        return sum;  
    }
  
    public static Mat segmentImage(Mat img){
        int w = 8;
        int rs = img.rows();
        int cs = img.cols();
        for(int i = 0; i < img.rows(); i+= w){
            for(int j = 0; j < img.cols(); j++){
                Mat tmp = img.apply(new opencv_core.Rect(j, i, Math.min(w, rs - j), Math.min(w, cs - i)));
                double mea = meanMatrix(tmp);
                double var = variance(tmp, mea);
                if(var < 40){
                    UByteRawIndexer idx = tmp.createIndexer();
                    for(int r = 0; r < tmp.rows(); r++){
                        for(int c = 0; c < tmp.cols(); c++){
                            idx.put(r, c, 0);
                        }
                    }
                }
            }
        }
        return img;
    }
    
    public Mat[] sobelDerivatives(Mat img){
        opencv_core.MatExpr zero = Mat.zeros(new Size(img.rows(), img.cols()), CV_32F);
    	Mat grad_x = zero.asMat();
        Mat grad_y = zero.asMat();
        FloatRawIndexer xId = grad_x.createIndexer();
        FloatRawIndexer yId = grad_y.createIndexer();
        for(int i = 1; i < img.rows() - 1; i++){
            for(int j = 1; j < img.cols() - 1; j++){
                float px = sobelDerivativeX(i, j, img);
                float py = sobelDerivativeY(i, j, img);
                xId.put(i, j,  px);
                yId.put(i, j,  py);
            }
        }
        return new Mat[]{grad_x, grad_y};
    }
    
    public float sobelDerivativeX(int x, int y, Mat img){
        UByteRawIndexer idx = img.createIndexer();
        return -idx.get(x - 1, y - 1) - 2 * idx.get(x, y - 1) - idx.get(x + 1, y - 1)
                + idx.get(x - 1, y + 1) + 2 * idx.get(x, y + 1) + idx.get(x + 1, y + 1);
                
    }
   
    public float sobelDerivativeY(int x, int y, Mat img){
        UByteRawIndexer idx = img.createIndexer();
        return -idx.get(x - 1, y - 1) - 2*idx.get(x - 1, y) - idx.get(x - 1, y + 1) 
                + idx.get(x + 1, y - 1) + 2*idx.get(x + 1, y) + idx.get(x + 1, y + 1);
    }
    
    public double blockOrientation( Mat blck_sbl_x, Mat blck_sbl_y) {
    	double v_x = 0;
    	double v_y = 0;
        int w = blck_sbl_x.rows();
    	FloatRawIndexer idx_x = blck_sbl_x.createIndexer();
    	FloatRawIndexer idx_y = blck_sbl_y.createIndexer();
    	for(int u = 0; u < w; u++){
            for(int v = 0; v < w; v++){
                v_x += 2 * idx_x.get(u, v) * idx_y.get(u, v);
                v_y += (idx_x.get(u, v)*idx_x.get(u, v) - idx_y.get(u, v)*idx_y.get(u, v));
            }
    	}
    	double theta = 0.5 * (Math.PI + Math.atan2(v_x, v_y));
    	return theta;
    }

    public Mat localOrientation(Mat img, Mat blck_sbl_x, Mat blck_sbl_y){
    	int w = 10;
    	Mat oriImg = new Mat(blck_sbl_x.rows()/ w, blck_sbl_x.cols()/ w, opencv_core.CV_64F);
    	DoubleRawIndexer idx = oriImg.createIndexer();
    	int ori_i = 0;
    	int ori_j = 0;
        int r =  blck_sbl_x.rows();
        int c = blck_sbl_y.cols();
    	for(int i = 1; i <  blck_sbl_x.rows(); i += w){
            for(int j = 1; j < blck_sbl_x.cols(); j += w){
                Mat sbX = blck_sbl_x.apply(new opencv_core.Rect(j, i, Math.min(w, r - j), Math.min(w, c - i)));
                Mat sbY = blck_sbl_y.apply(new opencv_core.Rect(j, i, Math.min(w, r - j), Math.min(w, c - i)));
                double ori = blockOrientation(sbX, sbY);
                idx.put(ori_i,ori_j, ori);
                ori_j ++;
            }
            ori_j = 0;
            ori_i ++;
        }
    	return oriImg;
    }
    
    public Mat[] localSmooth(Mat img){
    	Mat[] sb = sobelDerivatives(img);
    	Mat ori = localOrientation(img, sb[0], sb[1]);
    	Mat sinY = ori.clone();
    	Mat cosX = ori.clone();
    	DoubleRawIndexer idx_x = cosX.createIndexer();
    	DoubleRawIndexer idx_y = sinY.createIndexer();
    	for(int i = 0; i < ori.rows(); i++){
            for(int j = 0; j < ori.cols(); j++){
                idx_x.put(i, j, Math.cos(2 * idx_x.get(i, j)));
                idx_y.put(i, j, Math.sin(2 * idx_y.get(i, j)));
            }
    	}
       
    	return new Mat[]{ cosX, sinY};
    }

    public Mat smoothedOrientation(Mat cosX, Mat sinY){
        GaussianBlur(cosX, cosX, new Size(3, 3), 0, 0, BORDER_DEFAULT);
        GaussianBlur(sinY, sinY, new Size(3, 3), 0, 0, BORDER_DEFAULT);
        Mat smooth = new Mat(cosX.size(), cosX.type());
        DoubleRawIndexer idx_s = smooth.createIndexer();
        DoubleRawIndexer idx_x = cosX.createIndexer();
        DoubleRawIndexer idx_y = sinY.createIndexer();
        for(int i = 0; i < smooth.rows(); i++){
            for(int j = 0; j < smooth.cols(); j++){
                double theta =  Math.atan2(idx_y.get(i, j), idx_x.get(i, j)) / 2;
                idx_s.put(j, i, theta);
            }
        }
    	return smooth;
    }
      
    public Mat poinCare(Mat smooth, Mat img, String name){
        opencv_core.MatExpr siglar = new Mat().zeros(smooth.size(), CV_32F);
        Mat label = siglar.asMat();
    	FloatRawIndexer index = label.createIndexer();
        Mat rgb = new Mat();
        ArrayList<Point> coor = new ArrayList<>();
        Mat crop = new Mat();
        opencv_imgproc.cvtColor(img, rgb, CV_GRAY2RGB);
    	for(int r = 1; r < smooth.rows() - 1; r ++){
            for(int c = 1; c < smooth.cols() - 1; c ++){
                float beta = calcNeighbors(smooth, r, c);
                index.put(r, c, beta);
            }
        }

        for(int r = 0; r < label.rows(); r ++){
            for(int c = 0; c < label.cols(); c ++){
                if(index.get(r, c) == 1){
                    Point a = new Point(r, c);
                    coor.add(a);
                }
            }
        }

        if(coor.size() == 0){
           //System.out.println(name);
           opencv_imgproc.circle(rgb, new opencv_core.Point(6 * 10, 6 * 10), 3, org.bytedeco.javacpp.helper.opencv_core.AbstractScalar.CYAN);
           crop = img.apply(new Rect(0, 0, 90, 90));
        }
        else{
            Point c = findCorePoint(coor);
            c.setX(c.getX() * 10);
            c.setY(c.getY()* 10);
            //System.out.println("before: " + c.getX() + " " +c.getY());
            crop = cropFingerprint(c, img);
            //System.out.println("affter: " + c.getX() + " " + c.getY());
            opencv_imgproc.circle(rgb, new opencv_core.Point((int)c.getX() , (int)c.getY() ), 3, org.bytedeco.javacpp.helper.opencv_core.AbstractScalar.CYAN);
        }
        imwrite(name, crop);
        return label;
    }
    
    public Mat cropFingerprint(Point p, Mat img){
        Mat crop = new Mat();
        int radius = 20;
        int col = img.cols();
        int row = img.rows();
        //System.out.println(p.getX() + " " + p.getY());
        if(p.getY() > 0 && p.getY() <= 60){
            //System.out.println("1");
            crop = areaBlock(img, p);
        }
        if(p.getY() > 60 && p.getY() <= 120){
            //System.out.println("2");
            crop = areaBlockSecond(img, p);
        }
        return crop;
    }
    
    
    public Mat areaBlock(Mat img, Point p){
        Mat crop = new Mat();
        if(p.getX() > 90){
            //System.out.println("1_1");
            crop = img.apply(new Rect(30, 0, 90, 90));
        }
        if((p.getX() > 60 && p.getX() <= 90)){
            //System.out.println("1_2");
            crop = img.apply(new Rect(30, 0, 90, 90));
        }
        if(p.getX() > 30 && p.getX() <= 60){
            //System.out.println("1_3");
            crop = img.apply(new Rect(0, 0, 90, 90));
        }
        if(p.getX() > 0 && p.getX() <= 30){
            //System.out.println("1_4");
            crop = img.apply(new Rect(0, 0, 90, 90));
        }
        return crop;
    }
    public Mat areaBlockSecond(Mat img, Point p){
        Mat crop = new Mat();
        if(p.getX() > 90){
            //System.out.println("2_1");
            crop = img.apply(new Rect(30, 30, 90, 90));
        }
        if((p.getX() > 60 && p.getX() <= 90)){
            //System.out.println("2_2");
            crop = img.apply(new Rect(30, 30, 90, 90));
        }
        if(p.getX() > 30 && p.getX() <= 60){
            //System.out.println("2_3");
            crop = img.apply(new Rect(0, 30, 90, 90));
        }
        if(p.getX() > 0 && p.getX() <= 30){
           // System.out.println("2_4");
            crop = img.apply(new Rect(0, 30, 90, 90));
        }
        return crop;
    }
    public float calcNeighbors(Mat smooth, int i, int j){
        double[] deg = convertToDeg(smooth, i, j);
        double label = 0;
        for(int ele = 0; ele < deg.length - 1; ele++){
            if(Math.abs(subAngles(deg[ele], deg[ele + 1]))> 90){
                deg[ele+1]+= 180;
            }
            label += subAngles(deg[ele], deg[ele+1]);
        }
        if (label <= (180)&& label >= (180 )){
            return 1;
        }
        return 0;
        
    }

    public Mat convertToDegrees(Mat radians){
        Mat degree = radians.clone();
        DoubleRawIndexer rad = radians.createIndexer();
        DoubleRawIndexer deg = degree.createIndexer();
        for(int i = 0; i < radians.rows(); i++){
            for(int j = 0; j < radians.cols(); j++){
                double d = Math.toDegrees(rad.get(i, j));
                if(d < 0){
                    d = 180 + d;
                }
                deg.put(i, j, d);
            }
        }
        return degree;
    }
    
    public double subAngles(double x, double y){
        double tmp = x - y;
        if(Math.abs(tmp) > 180){
            tmp =  -1 * Math.signum(tmp) * (360 - Math.abs(tmp));
        }
        return tmp;
    }
    
    public double[] convertToDeg(Mat radians, int x, int y){
        int[][] neg = {{-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}};
        double[] deg = new double[9];
        DoubleRawIndexer rad = radians.createIndexer();
        for( int i = 0; i < neg.length; i++){
            double d = Math.toDegrees(rad.get(x - neg[i][0], y - neg[i][1]));
            if(d < 0){
                d += 180;
            }
            deg[i] = d;
        }
        return deg;
    }
    
    public Point findCorePoint(ArrayList<Point> coordinates){
        ArrayList<ArrayList<Double>> dis = new ArrayList<ArrayList<Double>>();
        for(int i = 0; i < coordinates.size(); i++){
            ArrayList<Double> cor1 = new ArrayList<>();
            for( int j = 0; j < coordinates.size(); j++){
                double d = coordinates.get(i).distanceAnotherPoint(coordinates.get(j));
                if(d <= 2.0)
                    cor1.add(d);
            }
        }
        int index = 0;
        for(int i = 0; i < dis.size(); i++){
            if(dis.get(i).size() > index){
                index = i;
            }
        }
        return coordinates.get(index);
    }
    
}

// end here
