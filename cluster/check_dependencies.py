#!/usr/bin/env python
# coding: utf-8

import os

import numpy as np
from keras.preprocessing.image import ImageDataGenerator
from keras.preprocessing.image import img_to_array
from keras.preprocessing.image import load_img

import glob
import matplotlib.pyplot as plt

from keras.models import Model
from keras.layers import Input, Reshape, UpSampling2D
from keras.layers.core import Dropout
from keras.layers.convolutional import Conv2D
from keras.layers.pooling import MaxPooling2D
from keras.layers.merge import concatenate
from keras.optimizers import Adam

from keras.callbacks import EarlyStopping, ModelCheckpoint, TensorBoard