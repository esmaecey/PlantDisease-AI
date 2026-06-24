# 🌿 Mobile-Supported Hierarchical Vegetable Disease Detection System

🇬🇧 English version below  
🇹🇷 Türkçe açıklama aşağıdadır

# 🇬🇧 English

🌿 Mobile-Supported Hierarchical Vegetable Disease Detection System

A deep learning based mobile decision support system for vegetable disease diagnosis using leaf images.

📌 Project Overview

This project was developed as a Bachelor's Graduation Project in Computer Engineering.

The aim of the study is to detect vegetable species and plant diseases from leaf images using deep learning models and provide a mobile-supported decision support system.

Unlike traditional single-stage classification approaches, this project employs a hierarchical classification architecture, where:

Vegetable species is identified first.
Disease classification is performed according to the detected species.
Results are presented through an Android application.
Model decisions are visualized using Grad-CAM++.
🎯 Objectives
Early detection of plant diseases
Reducing agricultural losses
Providing mobile accessibility for farmers and agricultural experts
Improving model interpretability using Explainable AI techniques
🏗 System Architecture

The proposed system consists of three stages:

Leaf Image
      │
      ▼
Species Classification Model
      │
      ▼
Detected Species
      │
      ▼
Disease Classification Model
      │
      ▼
Disease Prediction
      │
      ▼
Grad-CAM++ Visualization
      │
      ▼
Android Decision Support Application
🧠 Deep Learning Models
Species Classification

Model:

EfficientNetB3
CBAM (Convolutional Block Attention Module)

Purpose:

Determine vegetable species from leaf images.

Detected Classes:

Tomato
Pepper
Disease Classification

After species prediction:

Tomato Disease Model

Classes:

Tomato Healthy
Early Blight
Late Blight
Leaf Mold
Septoria Leaf Spot
Spider Mites
Mosaic Virus
Yellow Leaf Curl Virus
Target Spot
Pepper Disease Model

Classes:

Pepper Healthy
Pepper Bacterial Spot
Pepper Fungal Disease
🔍 Explainable AI

To improve model transparency, Grad-CAM++ was integrated into the system.

Grad-CAM++ highlights image regions that contribute most to model predictions.

Benefits:

Improved interpretability
Increased trust in model outputs
Better analysis of model behavior
📊 Dataset

The dataset was created using:

PlantVillage Dataset
Additional publicly available leaf disease datasets
Data Processing

Applied preprocessing techniques:

Image resizing
CLAHE (Contrast Limited Adaptive Histogram Equalization)
Normalization
Data Augmentation

Data augmentation methods:

Rotation
Horizontal Flip
Vertical Flip
Zoom
Brightness Adjustment
⚖️ Class Imbalance Handling

Class imbalance was addressed using:

Oversampling
Focal Loss

These techniques improved minority class recognition performance.

🧮 Training Strategy

Training was performed in two stages:

Stage 1

Feature Extraction

Frozen EfficientNetB3 backbone
Classification head training
Stage 2

Fine-Tuning

Selected backbone layers unfrozen
End-to-end training
📱 Mobile Application

The Android application was developed using:

Kotlin
Jetpack Compose
Material Design 3

Features:

✅ Camera Capture

✅ Gallery Image Selection

✅ Species Prediction

✅ Disease Prediction

✅ Confidence Scores

✅ Grad-CAM++ Visualization

✅ Disease Information

✅ Recommendation System

📦 TensorFlow Lite Deployment

Trained models were converted to TensorFlow Lite format for mobile deployment.

Included TFLite models:

species_main.tflite
species_realworld.tflite
tomato_disease_clahe.tflite
general_disease_clahe.tflite

Benefits:

Reduced model size
Faster inference
Mobile device compatibility
🛠 Technologies Used
Artificial Intelligence
TensorFlow
Keras
TensorFlow Lite
Computer Vision
OpenCV
Grad-CAM++
Mobile Development
Android Studio
Kotlin
Jetpack Compose
Deep Learning
EfficientNetB3
CBAM
Focal Loss
📈 Example Output
Species Prediction:
Pepper (%99.96)

Disease Prediction:
Fungal Disease (%62.16)

Recommendation:
Avoid excessive humidity and improve air circulation.
Consult agricultural experts if symptoms continue.
🚀 Future Improvements
Support for more vegetable species
Real-time camera inference
Cloud synchronization
Disease severity estimation
Multi-language support
Larger real-world dataset integration
🎓 Academic Information

Author: Esma Ece Yılmaz

Department of Computer Engineering

Karadeniz Technical University

Graduation Project

📜 License

This project was developed for academic and educational purposes.

⭐ If you found this project useful

Please consider giving it a star on GitHub.

🇹🇷 Türkçe
📌 Proje Hakkında

Bu proje, Karadeniz Teknik Üniversitesi Bilgisayar Mühendisliği Bölümü bitirme projesi kapsamında geliştirilmiştir.

Çalışmanın amacı, yaprak görüntülerinden sebze türü ve hastalık durumunu otomatik olarak tespit edebilen, derin öğrenme tabanlı mobil destekli bir karar destek sistemi geliştirmektir.

Geleneksel tek aşamalı sınıflandırma yaklaşımlarının aksine sistem, hiyerarşik sınıflandırma mimarisi kullanmaktadır.

Sistem şu adımlardan oluşmaktadır:

Yaprak görüntüsünden bitki türünün tespit edilmesi
Tespit edilen türe göre uygun hastalık modelinin çalıştırılması
Hastalık tahmininin gerçekleştirilmesi
Sonuçların Android uygulaması üzerinden kullanıcıya sunulması
Grad-CAM++ ile model kararlarının görselleştirilmesi
🎯 Projenin Amacı
Bitki hastalıklarının erken teşhis edilmesi
Tarımsal kayıpların azaltılması
Mobil cihazlar üzerinden erişilebilir bir karar destek sistemi sunulması
Yapay zekâ modellerinin açıklanabilirliğinin artırılması
🏗 Sistem Mimarisi
Yaprak Görüntüsü
        │
        ▼
Bitki Türü Sınıflandırma Modeli
        │
        ▼
Tespit Edilen Tür
        │
        ▼
Hastalık Sınıflandırma Modeli
        │
        ▼
Hastalık Tahmini
        │
        ▼
Grad-CAM++ Görselleştirmesi
        │
        ▼
Mobil Karar Destek Sistemi
🧠 Kullanılan Derin Öğrenme Modelleri
Tür Sınıflandırma Modeli
EfficientNetB3
CBAM (Convolutional Block Attention Module)

Tespit edilen türler:

Domates
Biber
Hastalık Sınıflandırma Modelleri
Domates Hastalıkları
Sağlıklı
Erken Yanıklık (Early Blight)
Geç Yanıklık (Late Blight)
Yaprak Küfü
Septoria Yaprak Lekesi
Kırmızı Örümcek
Mozaik Virüsü
Sarı Yaprak Kıvırcıklık Virüsü
Target Spot
Biber Hastalıkları
Sağlıklı
Bakteriyel Leke
Mantar Hastalığı
🔍 Açıklanabilir Yapay Zekâ

Model kararlarının yorumlanabilmesi amacıyla Grad-CAM++ yöntemi kullanılmıştır.

Bu yöntem sayesinde modelin görüntünün hangi bölgelerine odaklanarak karar verdiği görselleştirilebilmektedir.

📊 Veri Seti

Veri seti aşağıdaki kaynaklardan oluşturulmuştur:

PlantVillage Veri Seti
Ek açık kaynak bitki hastalığı veri setleri

Uygulanan ön işleme yöntemleri:

Yeniden boyutlandırma
CLAHE
Normalizasyon
Veri artırma (Data Augmentation)
⚖️ Sınıf Dengesizliği Problemi

Sınıf dengesizliğini azaltmak amacıyla:

Oversampling
Focal Loss

yöntemleri kullanılmıştır.

📱 Mobil Uygulama

Mobil uygulama aşağıdaki teknolojiler kullanılarak geliştirilmiştir:

Kotlin
Jetpack Compose
Material Design 3
TensorFlow Lite

Uygulama özellikleri:

✅ Kamera ile görüntü alma

✅ Galeriden görüntü seçme

✅ Tür tahmini

✅ Hastalık tahmini

✅ Güven skorları

✅ Grad-CAM++ görselleştirmesi

✅ Hastalık önerileri

🛠 Kullanılan Teknolojiler
Yapay Zekâ
TensorFlow
Keras
TensorFlow Lite
Görüntü İşleme
OpenCV
CLAHE
Grad-CAM++
Mobil Geliştirme
Android Studio
Kotlin
Jetpack Compose
Derin Öğrenme
EfficientNetB3
CBAM
Focal Loss
🚀 Gelecekteki Çalışmalar
Daha fazla sebze türü desteği
Gerçek zamanlı kamera analizi
Bulut tabanlı senkronizasyon
Hastalık şiddet analizi
Çoklu dil desteği
Daha büyük gerçek dünya veri setleri

🎓 Akademik Bilgiler

Esma Ece Yılmaz

Karadeniz Teknik Üniversitesi

Bilgisayar Mühendisliği Bölümü

Bitirme Projesi
