-- An attraction shows one photo: the list of images is not used and is dropped,
-- the photo columns lose the "main_" prefix
DROP TABLE attraction_images;

ALTER TABLE attractions
    RENAME COLUMN main_image TO image,
    RENAME COLUMN main_image_width TO image_width,
    RENAME COLUMN main_image_height TO image_height;
