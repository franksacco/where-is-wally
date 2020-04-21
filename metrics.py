#!/usr/bin/env python
# coding: utf-8

from keras import backend as K


def recall(y_true, y_pred):
    """Recall metric.
    
    Recall is the number of correct positive results divided by
    the number of all relevant samples (all samples that should
    have been identified as positive).
    """
    true_positives = K.sum(K.round(K.clip(y_true * y_pred, 0, 1)))
    possible_positives = K.sum(K.round(K.clip(y_true, 0, 1)))
    recall = true_positives / (possible_positives + K.epsilon())
    return recall


def precision(y_true, y_pred):
    """Precision metric.
    
    Precision is the number of correct positive results divided by
    the number of all positive results returned by the classifier.
    """
    true_positives = K.sum(K.round(K.clip(y_true * y_pred, 0, 1)))
    predicted_positives = K.sum(K.round(K.clip(y_pred, 0, 1)))
    precision = true_positives / (predicted_positives + K.epsilon())
    return precision


def f1_score(y_true, y_pred):
    """F1 score metric.
    
    The F1 score is the harmonic mean of the precision and
    recall, where an F1 score reaches its best value at 1
    (perfect precision and recall). 
    """
    p = precision(y_true, y_pred)
    r = recall(y_true, y_pred)
    return 2 * ( (p * r) / (p + r + K.epsilon()) )