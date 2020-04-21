#!/usr/bin/env python
# coding: utf-8

from keras import backend as K
import tensorflow as tf


def binary_focal_loss(gamma=2.0, alpha=0.25):
    """
    Implementation of Focal Loss from the paper in multiclass classification
    Formula:
        loss = -alpha_t*((1-p_t)^gamma)*log(p_t)
        p_t = y_pred, if y_true = 1
        p_t = 1-y_pred, otherwise
        alpha_t = alpha, if y_true=1
        alpha_t = 1-alpha, otherwise
        cross_entropy = -log(p_t)
    Parameters:
        alpha -- the same as wighting factor in balanced cross entropy
        gamma -- focusing parameter for modulating factor (1-p)
    Default value:
        gamma -- 2.0 as mentioned in the paper
        alpha -- 0.25 as mentioned in the paper
    """
    def binary_focal(y_true, y_pred):
        # Define epsilon so that the backpropagation will not result in NaN
        # for 0 divisor case
        epsilon = K.epsilon()
        # Clip the prediciton value
        y_pred = K.clip(y_pred, epsilon, 1.0-epsilon)
        # Calculate p_t
        p_t = tf.where(K.equal(y_true, 1), y_pred, 1-y_pred)
        # Calculate alpha_t
        alpha_factor = K.ones_like(y_true)*alpha
        alpha_t = tf.where(K.equal(y_true, 1), alpha_factor, 1-alpha_factor)
        # Calculate cross entropy
        cross_entropy = -K.log(p_t)
        weight = alpha_t * K.pow((1-p_t), gamma)
        # Calculate focal loss
        loss = weight * cross_entropy
        # Sum the losses in mini_batch
        loss = K.sum(loss, axis=1)
        return loss

    return binary_focal


def weighted_binary_crossentropy(y_true, y_pred):
    """
    Weighted binary crossentropy
    """
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
    """
    Dice = (2*|X & Y|)/ (|X|+ |Y|)
         =  2*sum(|A*B|)/(sum(A^2)+sum(B^2))
    
    Here is a dice loss for keras which is smoothed to approximate
    a linear (L1) loss. It ranges from 1 to 0 (no error), and
    returns results similar to binary crossentropy.
    """
    intersection = K.sum(y_true * y_pred, axis=-1)
    union = K.sum(K.square(y_true), axis=-1) + K.sum(K.square(y_pred), axis=-1)
    return 1 - (2. * intersection + smooth) / (union + smooth)


def jaccard_loss(y_true, y_pred, smooth=100):
    """
    Jaccard = (|X & Y|)/ (|X|+ |Y| - |X & Y|)
            = sum(|A*B|)/(sum(|A|)+sum(|B|)-sum(|A*B|))
    
    The jaccard distance loss is usefull for unbalanced datasets.
    This has been shifted so it converges on 0 and is smoothed to
    avoid exploding or disapearing gradient.
    """
    intersection = K.sum(K.abs(y_true * y_pred), axis=-1)
    summed = K.sum(K.abs(y_true) + K.abs(y_pred), axis=-1)
    jac = (intersection + smooth) / (summed - intersection + smooth)
    return (1 - jac) * smooth


losses = {
    'bifo': binary_focal_loss(),
    'wbce': weighted_binary_crossentropy,
    'dice': dice_loss,
    'jacc': jaccard_loss
}