package _go

import (
	"bytes"
)

type RawCodec struct{}

func (c *RawCodec) Marshal(v interface{}) ([]byte, error) {
	return v.([]byte), nil
}

func (c *RawCodec) Unmarshal(data []byte, v interface{}) error {
	result := v.(*bytes.Buffer)

	result.Reset()
	_, err := result.Write(data)
	result.Truncate(len(data))
	return err
}

func (c *RawCodec) Name() string {
	return "raw"
}
