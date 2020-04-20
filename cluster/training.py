#!/usr/bin/env python
# coding: utf-8

import os

import numpy as np
from keras.preprocessing.image import ImageDataGenerator
from keras.preprocessing.image import img_to_array
from keras.preprocessing.image import load_img

import glob

from keras import backend as K
from keras.models import Model
from keras.layers import Input, Reshape, UpSampling2D
from keras.layers.core import Dropout
from keras.layers.convolutional import Conv2D
from keras.layers.pooling import MaxPooling2D
from keras.layers.merge import concatenate
from keras.optimizers import Adam
from keras.callbacks import EarlyStopping, ModelCheckpoint, TensorBoard


TRAIN_PATH  = os.path.join(os.getcwd(), 'data', 'train')
MODELS_PATH = os.path.join(os.getcwd(), 'models')

IMG_WIDTH    = 256
IMG_HEIGHT   = 256
IMG_CHANNELS = 3

SEED             = 1
VALIDATION_SPLIT = 0.1
BATCH_SIZE       = 16
STEPS_PER_EPOCH  = 32
EPOCHS           = 20
LOSS_FUNCTION    = 'wbce'  # 'wbce' or 'dice' or 'jacc'


# Data Augmentation

data_gen_args = dict(rotation_range=20,
                     zoom_range=0.15,
                     width_shift_range=0.2,
                     height_shift_range=0.2,
                     shear_range=0.15,
                     horizontal_flip=True,
                     fill_mode='reflect',
                     validation_split=VALIDATION_SPLIT)

mask_data_gen = ImageDataGenerator(**data_gen_args)

data_gen_args['brightness_range'] = (0.6, 1.4) # we don't want to apply brightness to masks
image_data_gen = ImageDataGenerator(**data_gen_args)

flow_args = dict(directory=TRAIN_PATH,
                 target_size=(IMG_HEIGHT, IMG_WIDTH),
                 class_mode=None,
                 batch_size=BATCH_SIZE,
                 seed=SEED)

train_image_gen = image_data_gen.flow_from_directory(**flow_args,
                                                     classes=['images'],
                                                     subset='training')
train_mask_gen = mask_data_gen.flow_from_directory(**flow_args,
                                                   classes=['masks'],
                                                   color_mode='grayscale',
                                                   subset='training')

validation_image_gen = image_data_gen.flow_from_directory(**flow_args,
                                                          classes=['images'],
                                                          subset='validation')
validation_mask_gen = mask_data_gen.flow_from_directory(**flow_args,
                                                        classes=['masks'],
                                                        color_mode='grayscale',
                                                        subset='validation')

def normalize_inputs(img, mask):
    img = img / 255
    mask = mask / 255
    mask[mask > 0.5] = 1
    mask[mask <= 0.5] = 0
    return img, mask

def training_gen():
    while True:
        for img, mask in zip(train_image_gen, train_mask_gen):
            yield normalize_inputs(img, mask)
            
def validation_gen():
    while True:
        for img, mask in zip(validation_image_gen, validation_mask_gen):
            yield normalize_inputs(img, mask)


# Loss functions

def weighted_binary_crossentropy(y_true, y_pred):
    # Calulate weights    
    zero_count = K.sum(1 - y_true)
    one_count = K.sum(y_true)
    tot = one_count + zero_count
    zero_weight = 1 - zero_count / tot
    one_weight = 1 - one_count / tot
    
    # Calculate the binary crossentropy
    bce = K.binary_crossentropy(y_true, y_pred)
    
    # Apply the weights and return the mean error
    weight_vector = y_true * one_weight + (1. - y_true) * zero_weight
    return K.mean(weight_vector * bce)

def dice_loss(y_true, y_pred, smooth=1):
    intersection = K.sum(y_true * y_pred, axis=-1)
    union = K.sum(K.square(y_true), axis=-1) + K.sum(K.square(y_pred), axis=-1)
    return 1 - (2. * intersection + smooth) / (union + smooth)

def jaccard_loss(y_true, y_pred, smooth=100):
    intersection = K.sum(K.abs(y_true * y_pred), axis=-1)
    summed = K.sum(K.abs(y_true) + K.abs(y_pred), axis=-1)
    jac = (intersection + smooth) / (summed - intersection + smooth)
    return (1 - jac) * smooth

losses = {
    'wbce': weighted_binary_crossentropy,
    'dice': dice_loss,
    'jacc': jaccard_loss
}


# Metrics

def recall(y_true, y_pred):
    true_positives = K.sum(K.round(K.clip(y_true * y_pred, 0, 1)))
    possible_positives = K.sum(K.round(K.clip(y_true, 0, 1)))
    recall = true_positives / (possible_positives + K.epsilon())
    return recall

def precision(y_true, y_pred):
    true_positives = K.sum(K.round(K.clip(y_true * y_pred, 0, 1)))
    predicted_positives = K.sum(K.round(K.clip(y_pred, 0, 1)))
    precision = true_positives / (predicted_positives + K.epsilon())
    return precision

def f1_score(y_true, y_pred):
    p = precision(y_true, y_pred)
    r = recall(y_true, y_pred)
    return 2 * ( (p * r) / (p + r + K.epsilon()) )


# Model definition

def unet(pretrained_weights=None):

    inputs = Input((IMG_WIDTH, IMG_HEIGHT, IMG_CHANNELS))
    
    conv1 = Conv2D(64, 3, activation='relu', padding='same', kernel_initializer='he_normal')(inputs)
    conv1 = Conv2D(64, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv1)
    pool1 = MaxPooling2D(pool_size=(2,2))(conv1)
    
    conv2 = Conv2D(128, 3, activation='relu', padding='same', kernel_initializer='he_normal')(pool1)
    conv2 = Conv2D(128, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv2)
    pool2 = MaxPooling2D(pool_size=(2,2))(conv2)
    
    conv3 = Conv2D(256, 3, activation='relu', padding='same', kernel_initializer='he_normal')(pool2)
    conv3 = Conv2D(256, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv3)
    pool3 = MaxPooling2D(pool_size=(2,2))(conv3)
    
    conv4 = Conv2D(512, 3, activation='relu', padding='same', kernel_initializer='he_normal')(pool3)
    conv4 = Conv2D(512, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv4)
    drop4 = Dropout(0.5)(conv4)
    pool4 = MaxPooling2D(pool_size=(2, 2))(drop4)

    conv5 = Conv2D(1024, 3, activation='relu', padding='same', kernel_initializer='he_normal')(pool4)
    conv5 = Conv2D(1024, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv5)
    drop5 = Dropout(0.5)(conv5)

    up6 = Conv2D(512, 2, activation='relu', padding='same', kernel_initializer='he_normal')(UpSampling2D(size=(2,2))(drop5))
    merge6 = concatenate([drop4,up6], axis = 3)
    conv6 = Conv2D(512, 3, activation='relu', padding='same', kernel_initializer='he_normal')(merge6)
    conv6 = Conv2D(512, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv6)

    up7 = Conv2D(256, 2, activation='relu', padding='same', kernel_initializer='he_normal')(UpSampling2D(size=(2,2))(conv6))
    merge7 = concatenate([conv3,up7], axis = 3)
    conv7 = Conv2D(256, 3, activation='relu', padding='same', kernel_initializer='he_normal')(merge7)
    conv7 = Conv2D(256, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv7)

    up8 = Conv2D(128, 2, activation='relu', padding='same', kernel_initializer='he_normal')(UpSampling2D(size=(2,2))(conv7))
    merge8 = concatenate([conv2,up8], axis = 3)
    conv8 = Conv2D(128, 3, activation='relu', padding='same', kernel_initializer='he_normal')(merge8)
    conv8 = Conv2D(128, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv8)

    up9 = Conv2D(64, 2, activation='relu', padding='same', kernel_initializer='he_normal')(UpSampling2D(size=(2,2))(conv8))
    merge9 = concatenate([conv1,up9], axis = 3)
    conv9 = Conv2D(64, 3, activation='relu', padding='same', kernel_initializer='he_normal')(merge9)
    conv9 = Conv2D(64, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv9)
    conv9 = Conv2D(2, 3, activation='relu', padding='same', kernel_initializer='he_normal')(conv9)
    
    conv10 = Conv2D(1, 1, activation='sigmoid')(conv9)

    model = Model(inputs=[inputs], outputs=[conv10])
    model.compile(
        optimizer=Adam(lr=1e-4),
        loss=losses[LOSS_FUNCTION],
        metrics=[f1_score, precision, recall]
    )

    if pretrained_weights:
        model.load_weights(pretrained_weights)

    return model


# Training

model = unet()

earlystopper = EarlyStopping(monitor='loss',
                             patience=5,
                             verbose=1)

name = 'unet-%s-b%d-s%d-e%d.{epoch:02d}.hdf5' % (LOSS_FUNCTION, BATCH_SIZE, STEPS_PER_EPOCH, EPOCHS)
checkpointer = ModelCheckpoint(os.path.join(MODELS_PATH, name),
                               monitor='val_f1_score',
                               save_best_only=True,
                               save_weights_only=False,
                               mode='max',
                               verbose=1)

tensorboard = TensorBoard(log_dir='logs')


model.fit_generator(training_gen(),
                    steps_per_epoch=STEPS_PER_EPOCH,
                    epochs=EPOCHS,
                    callbacks=[earlystopper, checkpointer, tensorboard],
                    validation_data=validation_gen(),
                    validation_steps=2)