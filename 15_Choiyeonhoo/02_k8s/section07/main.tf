# Kubernetes 프로바이더 설정
provider "kubernetes" {
  config_path = "~/.kube/config"  # 로컬 Kubernetes 설정 파일 경로
}

# Spring Boot Deployment
resource "kubernetes_deployment_v1" "boot007dep" {
  metadata {
    name = "boot007dep"
  }

  spec {
    replicas = 3

    selector {
      match_labels = {
        app = "boot007kube"
      }
    }

    template {
      metadata {
        labels = {
          app = "boot007kube"
        }
      }

      spec {
        container {
          image = "cyhhoo/k8s-boot-ing:latest"
          name  = "boot-container"
          image_pull_policy = "Always"

          port {
            container_port = 8080
          }
        }
      }
    }
  }
}

# Spring Boot Service
resource "kubernetes_service_v1" "boot007ser" {
  metadata {
    name = "boot007ser"
  }

  spec {
    selector = {
      app = "boot007kube"
    }

    port {
      port        = 8001
      target_port = 8080
    }

    type = "ClusterIP"
  }
}

# Vue.js Deployment
resource "kubernetes_deployment_v1" "vue007dep" {
  metadata {
    name = "vue007dep"
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "vue007kube"
      }
    }

    template {
      metadata {
        labels = {
          app = "vue007kube"
        }
      }

      spec {
        container {
          image = "cyhhoo/k8s-vue-ing:latest"
          name  = "vue-container"
          image_pull_policy = "Always"

          port {
            container_port = 80
          }
        }
      }
    }
  }
}

# Vue.js Service
resource "kubernetes_service_v1" "vue007ser" {
  metadata {
    name = "vue007ser"
  }

  spec {
    selector = {
      app = "vue007kube"
    }

    port {
      port        = 8000
      target_port = 80
    }

    type = "ClusterIP"
  }
}

# Ingress
resource "kubernetes_ingress_v1" "sw_camp_ingress" {
  metadata {
    name = "sw-camp-ingress"
    annotations = {
      "nginx.ingress.kubernetes.io/ssl-redirect" = "false"
      "nginx.ingress.kubernetes.io/rewrite-target" = "/$2"
    }
  }

  spec {
    ingress_class_name = "nginx"

    rule {
      http {
        path {
          path = "/()(.*)$"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "vue007ser"
              port {
                number = 8000
              }
            }
          }
        }
        path {
          path = "/boot(/|$)(.*)$"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "boot007ser"
              port {
                number = 8001
              }
            }
          }
        }
      }
    }
  }
}