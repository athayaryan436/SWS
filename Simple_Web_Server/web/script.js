function ubahWarna() {
    var body = document.querySelector('body');
    var warna = body.style.backgroundColor === 'rgb(240, 240, 240)' ? '#e6f7ff' : '#f0f0f0';
    body.style.backgroundColor = warna;
  }
  