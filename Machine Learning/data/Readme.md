# Data preparation



The base dataset is the Stanford Dogs Dataset available on Kaggle <https://www.kaggle.com/jessicali9530/stanford-dogs-dataset>



Three more datasets have been used in order to extend dog breed groups with human and cat classes.

https://www.kaggle.com/zippyz/cats-and-dogs-breeds-classification-oxford-dataset/data

https://www.kaggle.com/maks23/cropped-dog-breeds

https://www.kaggle.com/tanvirshanto/facedatasetcelebrity



In order to understand the whole data preparation process, the following order is suggested:

1. raw dataset evaluate Notebook: evaluates the extended (human, cats, dogs together) but still "raw" dataset
2. dataset generator Notebook: generates training/validation/test sets and evaluates them
3. sample image generator Notebook: generates one sample image from all classes and names them after the Id of the picture's class



The dataset itself is not uploaded due to its size. However, samples folder contains one image from each class to exemplify the used images. The samples folder has been generated with the sample image generator Notebook.