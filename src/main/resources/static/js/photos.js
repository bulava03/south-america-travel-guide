// A photo that fails to load is shown like a place without a photo: the name on green.
// Loaded in <head>, so it catches errors of images that load before the page is parsed.
(function () {
    function showName(frame) {
        var named = frame.querySelector('img[alt]:not([alt=""])');
        var name = named ? named.alt : '';
        frame.querySelectorAll('img, .plate-print-bg, .photo-credit').forEach(function (element) {
            element.remove();
        });

        var background = document.createElement('div');
        background.className = 'plate-none';
        var label = document.createElement('span');
        label.className = 'plate-none-name';
        label.textContent = name;
        frame.insertBefore(label, frame.firstChild);
        frame.insertBefore(background, label);
    }

    // Image errors do not bubble, so they are caught in the capture phase
    document.addEventListener('error', function (event) {
        var image = event.target;
        if (image.tagName !== 'IMG') {
            return;
        }
        // Small photos in "More in ...": the green background is already there
        if (image.closest('.mini-photo')) {
            image.remove();
            return;
        }
        var frame = image.closest('.plate, .detail-photo');
        if (frame && !frame.classList.contains('photo-broken')) {
            frame.classList.add('photo-broken');
            showName(frame);
        }
    }, true);
})();
