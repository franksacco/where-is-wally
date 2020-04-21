#!/usr/bin/env python
# coding: utf-8

from keras.preprocessing.image import ImageDataGenerator


def data_generators(validation_split=0.1):
    data_gen_args = dict(
        rotation_range=20,
        zoom_range=0.15,
        width_shift_range=0.2,
        height_shift_range=0.2,
        shear_range=0.15,
        horizontal_flip=True,
        fill_mode='reflect',
        validation_split=validation_split
    )
    
    mask_data_gen = ImageDataGenerator(**data_gen_args)

    # we don't want to apply brightness to masks
    data_gen_args['brightness_range'] = (0.6, 1.4)
    image_data_gen = ImageDataGenerator(**data_gen_args)
    
    return image_data_gen, mask_data_gen
    

def training_generators(data_path,
                        image_size=(256, 256),
                        batch_size=32,
                        seed=None,
                        validation_split=0.1):
    image_data_gen, mask_data_gen = data_generators(validation_split)
    
    flow_args = dict(
        directory=data_path,
        target_size=image_size,
        class_mode=None,
        batch_size=batch_size,
        seed=seed,
        subset='training'
    )
    image_gen = image_data_gen.flow_from_directory(
        **flow_args,
        classes=['images']
    )
    mask_gen = mask_data_gen.flow_from_directory(
        **flow_args,
        classes=['masks'],
        color_mode='grayscale'
    )
    return image_gen, mask_gen


def validation_generators(data_path,
                          image_size=(256, 256),
                          batch_size=32,
                          seed=None,
                          validation_split=0.1):
    image_data_gen, mask_data_gen = data_generators(validation_split)
    
    flow_args = dict(
        directory=data_path,
        target_size=image_size,
        class_mode=None,
        batch_size=batch_size,
        seed=seed,
        subset='validation'
    )
    image_gen = image_data_gen.flow_from_directory(
        **flow_args,
        classes=['images']
    )
    mask_gen = mask_data_gen.flow_from_directory(
        **flow_args,
        classes=['masks'],
        color_mode='grayscale'
    )
    return image_gen, mask_gen


def normalize_inputs(img, mask):
    img = img / 255
    mask = mask / 255
    mask[mask > 0.5] = 1
    mask[mask <= 0.5] = 0
    return img, mask


def generator(generators):
    image_gen, mask_gen = generators
    while True:
        for img, mask in zip(image_gen, mask_gen):
            yield normalize_inputs(img, mask)


def training_gen(data_path,
                 image_size=(256, 256),
                 batch_size=32,
                 seed=None,
                 validation_split=0.1):
    """Training image generator"""
    return generator(training_generators(
        data_path=data_path,
        image_size=image_size,
        batch_size=batch_size,
        seed=seed,
        validation_split=validation_split
    ))


def validation_gen(data_path,
                   image_size=(256, 256),
                   batch_size=32,
                   seed=None,
                   validation_split=0.1):
    """Validation image generator"""
    return generator(validation_generators(
        data_path=data_path,
        image_size=image_size,
        batch_size=batch_size,
        seed=seed,
        validation_split=validation_split
    ))