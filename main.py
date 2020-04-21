#!/usr/bin/env python
# coding: utf-8


import argparse

# Training options
parser = argparse.ArgumentParser(description='U-net training on Where\'s Wally? dataset.')
parser.add_argument('-b', '--batch-size', default=16, type=int)
parser.add_argument('-s', '--steps-per-epoch', default=32, type=int)
parser.add_argument('-e', '--epochs', default=20, type=int)
parser.add_argument('-l', '--loss', default='bifo', choices=['bifo', 'wbce', 'dice', 'jacc'])
parser.add_argument('-S', '--seed', default=1, type=int)
args = parser.parse_args()


import glob
import numpy as np
import matplotlib.pyplot as plt
import os
from keras.callbacks import EarlyStopping, ModelCheckpoint, TensorBoard

from dataset import training_gen, validation_gen
from metrics import f1_score, precision, recall
from model import unet
from losses import *


IMG_WIDTH    = 256
IMG_HEIGHT   = 256
IMG_CHANNELS = 3

BATCH_SIZE       = args.batch_size
STEPS_PER_EPOCH  = args.steps_per_epoch
EPOCHS           = args.epochs
LOSS_FUNCTION    = args.loss
VALIDATION_SPLIT = 0.1
SEED             = args.seed

NAME = '%s-b%d-s%d-e%d' % (LOSS_FUNCTION, BATCH_SIZE, STEPS_PER_EPOCH, EPOCHS)


# Model definition
            
model = unet(loss=losses[LOSS_FUNCTION],
             metrics=[f1_score, precision, recall],
             input_size=(IMG_WIDTH, IMG_HEIGHT, IMG_CHANNELS))


# Callbacks

earlystopper = EarlyStopping(
    monitor='loss',
    patience=5,
    verbose=1
)
checkpointer = ModelCheckpoint(
    'models/unet.' + NAME + '.{epoch:02d}.hdf5',
    monitor='val_f1_score',
    save_best_only=True,
    save_weights_only=False,
    mode='max',
    verbose=1
)
tensorboard = TensorBoard(log_dir='logs')


# Training

train_gen = training_gen(
    data_path='data',
    image_size=(IMG_WIDTH, IMG_HEIGHT),
    batch_size=BATCH_SIZE,
    seed=SEED,
    validation_split=VALIDATION_SPLIT
)
val_gen = validation_gen(
    data_path='data',
    image_size=(IMG_WIDTH, IMG_HEIGHT),
    batch_size=BATCH_SIZE,
    seed=SEED,
    validation_split=VALIDATION_SPLIT
)

history = model.fit_generator(
    train_gen,
    steps_per_epoch=STEPS_PER_EPOCH,
    epochs=EPOCHS,
    callbacks=[earlystopper, checkpointer, tensorboard],
    validation_data=val_gen,
    validation_steps=2
)

# Plot training & validation f1-score values
plt.plot(history.history['f1_score'])
plt.plot(history.history['val_f1_score'])
plt.title('Model F1-score')
plt.ylabel('F1-score')
plt.xlabel('Epoch')
plt.legend(['Train', 'Validation'], loc='upper left')
plt.savefig('logs/f1_score.' + NAME + '.png')
plt.close()

# Plot training & validation loss values
plt.plot(history.history['loss'])
plt.plot(history.history['val_loss'])
plt.title('Model loss')
plt.ylabel('Loss')
plt.xlabel('Epoch')
plt.legend(['Train', 'Validation'], loc='upper left')
plt.savefig('logs/loss.' + NAME + '.png')
plt.close()