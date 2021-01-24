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


double confThreshold = 0.3;
double nmsThreshold = 0.3;
vector<string> labels;
Scalar color;
Net net;
int startTime;
double measTime;
double infTime;
struct timespec t1,t2;
double avgInfTime=0;
double totalInfTime=0;
int totalInfNumber=0;


Size prevSize(640,480);
Size detSize(416,416);



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
                                                          jlong matAddr, jint angle) {

    // get Mat from raw address
    Mat &mat = *(Mat *) matAddr;
    prevSize=Size(mat.cols, mat.rows);
    if(angle==270) {
        cv::rotate(mat,mat,cv::ROTATE_180);
    }

//    cv::cvtColor(mat,mat,COLOR_RGBA2RGB);
    cv::cvtColor(mat,mat,COLOR_RGBA2BGR);
    Mat blob;
    clock_gettime(CLOCK_MONOTONIC, &t1);
    dnn::blobFromImage(mat, blob, 1 / 255.0, detSize, Scalar(0, 0, 0), true, false);
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
                                                      jlong matAddr, jint angle) {

    // get Mat from raw address
    Mat &mat = *(Mat *) matAddr;
    if(angle==270) {
        cv::rotate(mat,mat,cv::ROTATE_180);
    }
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
    clock_gettime(CLOCK_MONOTONIC, &t2);
    measTime=1000.0*t2.tv_sec + 1e-6*t2.tv_nsec - (1000.0*t1.tv_sec + 1e-6*t1.tv_nsec);
    totalInfNumber++;
    infTime= measTime;
    totalInfTime+=infTime;
    avgInfTime=totalInfTime/totalInfNumber;
    for (size_t i = 0; i < indices.size(); ++i) {
        int idx = indices[i];
        Rect box = boxes[idx];
        drawPred(classIds[idx], confidences[idx], box.x, box.y, box.x + box.width, box.y + box.height, frame);
    }
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


extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_moneyhelper_YoloActivity_getPrevSize(JNIEnv *env, jobject thiz) {
    char prevSizeString[10];
    sprintf(prevSizeString,"%dx%d",prevSize.width,prevSize.height);
    return (*env).NewStringUTF(prevSizeString);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_moneyhelper_YoloActivity_getDetSize(JNIEnv *env, jobject thiz) {
    char detSizeString[10];
    sprintf(detSizeString,"%dx%d",detSize.width,detSize.height);
    return (*env).NewStringUTF(detSizeString);
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_moneyhelper_YoloActivity_getInfTime(JNIEnv *env, jobject thiz) {
    char infTimeString[10];
    sprintf(infTimeString,"%.0f ms",infTime);
    return (*env).NewStringUTF(infTimeString);
}extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_moneyhelper_YoloActivity_getAvgInfTime(JNIEnv *env, jobject thiz) {
    char avgInfTimeString[10];
    sprintf(avgInfTimeString, "%.1f ms", avgInfTime);
    return (*env).NewStringUTF(avgInfTimeString);
}extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_moneyhelper_YoloActivity_getInfNum(JNIEnv *env, jobject thiz) {
    char totalInfNumberString[10];
    sprintf(totalInfNumberString, "%d", totalInfNumber);
    return (*env).NewStringUTF(totalInfNumberString);
}