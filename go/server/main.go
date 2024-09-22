package main

import (
	"context"
	"fmt"
	"io"
	"log"
	"net"

	bidi "github.com/bsideup/grpc-bidi/go"
	"google.golang.org/grpc"
	"google.golang.org/grpc/encoding"
	health "google.golang.org/grpc/health/grpc_health_v1"
)

func main() {
	listener, err := net.Listen("tcp", "localhost:50051")
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}

	encoding.RegisterCodec(&bidi.RawCodec{})

	server := grpc.NewServer()

	bidi.RegisterServer(server, func(ctx context.Context, client *grpc.ClientConn) error {
		healthClient := health.NewHealthClient(client)

		c, err := healthClient.Watch(ctx, &health.HealthCheckRequest{})
		if err != nil {
			return fmt.Errorf("failed to watch health check: %w", err)
		}

		for {
			msg, err := c.Recv()

			if err == io.EOF {
				return nil
			}

			if err != nil {
				return fmt.Errorf("failed to receive message: %w", err)
			}

			log.Printf("ServerStream.Recv: %+v\n", msg)
		}
	})

	if err := server.Serve(listener); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
