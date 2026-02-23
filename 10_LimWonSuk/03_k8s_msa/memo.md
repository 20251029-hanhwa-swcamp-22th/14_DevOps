mani
kubectl apply -f configmap.yml
kubectl apply -f secret.yml  
kubectl apply -f mariadb.yml
kubectl apply -f rabbitmq.yml
kubectl apply -f order.yml   
kubectl apply -f payment.yml
kubectl apply -f inventory.yml
kubectl apply -f ingress.yml 

kubectl delete --all 
ingress,configmap,secret,pvc,services,deployments,pods
