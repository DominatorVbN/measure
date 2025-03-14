package codec

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"errors"
	"io"

	"github.com/klauspost/compress/zstd"
)

// IsTarGz validates that the expected file
// is a valid gzipped tarball.
func IsTarGz(file io.Reader) (err error) {
	// copy the contents of file into a buffer
	//
	// copy is essential here because otherwise
	// this function advances the file pointer
	// which then results into corrupted data
	// downstream.
	//
	// FIXME: implement a better check which
	// either copies a few bytes just to read
	// the magic number from the header or
	// a different approach to not advance
	// the file pointer and still be efficient.
	var buf bytes.Buffer
	if _, err = io.Copy(&buf, file); err != nil {
		return
	}

	// create a new reader from the buffer
	r := bytes.NewReader(buf.Bytes())

	// check if gzip file
	gzipReader, err := gzip.NewReader(r)
	if err != nil {
		if errors.Is(err, gzip.ErrHeader) {
			err = errors.Join(errors.New("not a valid gzip file"), err)
			return
		}
		err = errors.Join(errors.New("gzip check failed"), err)
		return
	}

	defer gzipReader.Close()

	// check if tar archive
	tarReader := tar.NewReader(gzipReader)
	_, err = tarReader.Next()
	if err != nil {
		err = errors.Join(errors.New("not a valid tar file"), err)
		return
	}

	return
}

// zstdMagic is the magic sequence for detecting
// zstd compressed data.
var zstdMagic = []byte{0x28, 0xB5, 0x2F, 0xFD}

// CompressZstd deflates input bytes using zstd.
func CompressZstd(uncompressed []byte) (compressed []byte, err error) {
	encoder, err := zstd.NewWriter(nil)
	if err != nil {
		return
	}
	defer encoder.Close()

	compressed = encoder.EncodeAll(uncompressed, make([]byte, 0, len(uncompressed)))

	return
}

// IsZstdCompressed determines if zstd has been
// used to compress data.
func IsZstdCompressed(data []byte) bool {
	if len(data) < len(zstdMagic) {
		return false
	}

	return bytes.Equal(data[:len(zstdMagic)], zstdMagic)
}
