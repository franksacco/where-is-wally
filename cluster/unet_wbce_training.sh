#!/bin/sh

#SBATCH --job-name=unet_wbce_training
#SBATCH --output=%x.o%j
#SBATCH --error=%x.e%j
#SBATCH --nodes=1
#SBATCH --gres=gpu:tesla:1
#SBATCH --partition=gpu
#SBATCH --mem=16G
#SBATCH --time=0-02:00:00

#SBATCH --mail-user=francesco.saccani2@studenti.unipr.it
#SBATCH --mail-type=BEGIN,FAIL,END


module load miniconda3
source "$CONDA_PREFIX/etc/profile.d/conda.sh"
conda activate machine-learning-cuda-10.0

python main.py --batch-size 16 --steps-per-epoch 32 --epochs 20 --loss wbce

conda deactivate
