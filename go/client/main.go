package main

import (
	"context"
	"log"
	"time"

	bidi "github.com/bsideup/grpc-bidi/go"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	health "google.golang.org/grpc/health/grpc_health_v1"
)

type simpleHealthService struct {
}

func (s *simpleHealthService) Check(context.Context, *health.HealthCheckRequest) (*health.HealthCheckResponse, error) {
	panic("unimplemented")
}

func (s *simpleHealthService) Watch(_ *health.HealthCheckRequest, watcher health.Health_WatchServer) error {
	for {
		select {
		case <-watcher.Context().Done():
			return watcher.Context().Err()
		default:
			resp := health.HealthCheckResponse{Status: health.HealthCheckResponse_SERVING}
			if err := watcher.Send(&resp); err != nil {
				return err
			}

			<-time.After(time.Second)
		}
	}
}

func main() {
	conn, err := grpc.NewClient(
		"localhost:50051",
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		log.Fatalf("fail to dial: %v", err)
	}
	defer conn.Close()

	server := grpc.NewServer()
	health.RegisterHealthServer(server, &simpleHealthService{})

	if err := server.Serve(bidi.NewListener(conn)); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
