#include <jni.h>
#include <string>
#include <android/log.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/dnn/dnn.hpp>
#include <fstream>
#include <random>
#include <ctime>
#include<cstdlib>

#define TAG "NativeLib"

using namespace std;
using namespace cv;
using namespace dnn;


double confThreshold = 0.5;
double nmsThreshold = 0.3;
vector<string> labels;
Scalar color;
Net net;
int startTime;
double measTime;
double fps;



void drawPred(int classId, float conf, int startX, int startY, int endX, int endY, Mat& frame);
vector<string> getOutputNames(Net& net);
void postprocess(Mat& frame, const vector<Mat>& outs);

extern "C" {
void JNICALL
Java_com_example_moneyhelper_YoloActivity_initializeNet(JNIEnv *env,
                                                        jobject instance,
                                                        jstring names,
                                                        jstring weights,
                                                        jstring config
                                                                        ) {
    string labelsPath = env->GetStringUTFChars( names, NULL );
    string weightsPath = env->GetStringUTFChars( weights, NULL );
    string configPath = env->GetStringUTFChars( config, NULL );
    string label;

    ifstream in(labelsPath);
    if (!in) {
        return;
    }
    while (getline(in, label)) {
        labels.push_back(label);
    }
    color = CV_RGB(255, 0, 0);
    try {
         net = dnn::readNetFromDarknet(configPath,weightsPath);
    }
    catch (std::exception const& ex) {
        __android_log_print(ANDROID_LOG_ERROR, "LOG", "%s", ex.what());
    }


}
}

extern "C" {
void JNICALL
Java_com_example_moneyhelper_YoloActivity_objectDetection(JNIEnv *env,
                                                          jobject instance,
                                                          jlong matAddr) {

    // get Mat from raw address
    Mat &mat = *(Mat *) matAddr;
//    cv::cvtColor(mat,mat,COLOR_RGBA2RGB);
    cv::cvtColor(mat,mat,COLOR_RGBA2BGR);
    Mat blob;
    startTime=clock();
    dnn::blobFromImage(mat, blob, 1 / 255.0, Size(416, 416), Scalar(0, 0, 0), true, false);
    net.setInput(blob);
    vector<Mat> outs;
    net.forward(outs, getOutputNames(net));
    postprocess(mat, outs);
    cv::cvtColor(mat,mat,COLOR_BGR2RGBA);
}
}

extern "C" {
void JNICALL
Java_com_example_moneyhelper_YoloActivity_returnFrame(JNIEnv *env,
                                                      jobject instance,
                                                      jlong matAddr) {

    // get Mat from raw address
    Mat &mat = *(Mat *) matAddr;
//    cv::cvtColor(mat,mat,COLOR_RGBA2BGR);
}
}


vector<string> getOutputNames(Net& net) {
    static vector<string> names;

    if (names.empty()) {
        vector<int> outLayers = net.getUnconnectedOutLayers();
        vector<string> layersNames = net.getLayerNames();

        names.resize(outLayers.size());

        for (size_t i = 0; i < outLayers.size(); ++i) {
            names[i] = layersNames[outLayers[i] - 1];
        }
    }
    return names;
}

void postprocess(Mat& frame, const vector<Mat>& outs) {
    vector<int> classIds;
    vector<float> confidences;
    vector<Rect> boxes;

    for (size_t i = 0; i < outs.size(); ++i) {
        float* data = (float*)outs[i].data;
        for (int j = 0; j < outs[i].rows; ++j, data += outs[i].cols) {
            Mat scores = outs[i].row(j).colRange(5, outs[i].cols);
            Point classIdPoint;
            double confidence;

            minMaxLoc(scores, 0, &confidence, 0, &classIdPoint);
            if (confidence > confThreshold) {
                int centerX = (int)(data[0] * frame.cols);
                int centerY = (int)(data[1] * frame.rows);
                int width = (int)(data[2] * frame.cols);
                int height = (int)(data[3] * frame.rows);
                int startX = centerX - width / 2;
                int startY = centerY - height / 2;

                classIds.push_back(classIdPoint.x);
                confidences.push_back((float)confidence);
                boxes.push_back(Rect(startX, startY, width, height));
            }
        }
    }

    vector<int> indices;
    NMSBoxes(boxes, confidences, confThreshold, nmsThreshold, indices);
    for (size_t i = 0; i < indices.size(); ++i) {
        int idx = indices[i];
        Rect box = boxes[idx];
        drawPred(classIds[idx], confidences[idx], box.x, box.y, box.x + box.width, box.y + box.height, frame);
    }
    measTime=(double)(clock()-startTime)/CLOCKS_PER_SEC;
    fps=measTime*1000;
    string text=format("%.1f ms",fps);
    int baseLine;
    int startX=15, startY=15;
    Size labelSize = getTextSize(text, FONT_HERSHEY_SIMPLEX, 1.2, 2, &baseLine);
    startY = max(startY, labelSize.height);
    putText(frame,text,Point(startX,startY),FONT_HERSHEY_SIMPLEX,1.2,CV_RGB(0,255,0),2);
}


void drawPred(int classId, float conf, int startX, int startY, int endX, int endY, Mat& frame) {
    rectangle(frame, Point(startX, startY), Point(endX, endY), color,5);
    string label = format("%.2f", conf);
    if (!labels.empty()) {
        CV_Assert(classId < (int)labels.size());
        label = labels[classId] + ":" + label;
    }

    int baseLine;
    Size labelSize = getTextSize(label, FONT_HERSHEY_SIMPLEX, 1.1, 2, &baseLine);
    startY = max(startY, labelSize.height);
    putText(frame, label, Point(startX, startY), FONT_HERSHEY_SIMPLEX, 1.1, color, 2);

}


