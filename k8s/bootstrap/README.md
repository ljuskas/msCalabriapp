# Bootstrap del runner de despliegue

Este directorio contiene el RBAC que necesita el **runner self-hosted dedicado**
para aplicar el Deployment y el Service de `msCalabriapp` en el namespace
existente `clabriapp`.

> **No aplicado en la tarea 23.3.** El runner no está registrado, el kubeconfig
> no está instalado y estos recursos no se han creado en el clúster. El
> operador debe revisarlos y aplicarlos cuando habilite el runner.

## Contenido

`deployer-rbac.yaml` crea:

- ServiceAccount `clabriapp-deployer` en `clabriapp`.
- Role namespace-scoped con permisos sobre `deployments`, `replicasets`,
  `services` y lectura de `pods`.
- RoleBinding entre la ServiceAccount y el Role.

**No hay ninguna regla sobre `secrets`.** El runner no puede leer el Secret
`clabriapp-postgresql`; la aplicación lo consume mediante `secretKeyRef` a
través del kubelet. Tampoco hay `ClusterRole` ni permisos sobre `namespaces`.

## Aplicación futura (no ejecutar en este encargo)

```bash
kubectl apply -f k8s/bootstrap/deployer-rbac.yaml
```

El namespace `clabriapp` debe existir de antemano. Este manifiesto no lo crea.

Después, el operador debe generar el kubeconfig del runner en
`/etc/mscalabriapp-runner/kubeconfig`, legible solo por el usuario del runner
(por ejemplo, modo `0600`). El kubeconfig se deriva de la ServiceAccount
`clabriapp-deployer`; el token o certificado **no** se versiona en este
repositorio ni se guarda en GitHub.

Comprobación de permisos con el kubeconfig instalado:

```bash
kubectl auth can-i patch deployments -n clabriapp   # yes
kubectl auth can-i get secrets -n clabriapp         # no
```

Si el segundo comando devuelve `yes`, el kubeconfig no corresponde a esta
ServiceAccount: hay que corregirlo antes de registrar el runner.

## Requisitos del runner

- Etiqueta exacta `clabriapp-local-main`.
- `kubectl` y `curl` instalados.
- `KUBECONFIG=/etc/mscalabriapp-runner/kubeconfig` presente y legible.
- **Sin Docker ni socket de Docker.** El workflow de despliegue no los usa.
- Salida a `ghcr.io` para el preflight anónimo de la imagen.
- El paquete GHCR debe ser público; la visibilidad es independiente de la del
  repositorio y debe cambiarse manualmente la primera vez.
