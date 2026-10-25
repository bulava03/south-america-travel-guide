-- Size of the main photo from Wikimedia Commons, used to choose how the photo is shown
ALTER TABLE attractions ADD COLUMN main_image_width INT NULL;
ALTER TABLE attractions ADD COLUMN main_image_height INT NULL;
