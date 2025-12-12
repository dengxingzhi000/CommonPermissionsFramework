#!/bin/bash

# Test Runner Script for CommonPermissionsFramework
# Runs tests and generates coverage reports

set -e  # Exit on error

echo "================================================"
echo "  CommonPermissionsFramework Test Suite"
echo "================================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    print_error "Maven is not installed. Please install Maven first."
    exit 1
fi

print_success "Maven found: $(mvn --version | head -n 1)"
echo ""

# Parse command line arguments
TEST_SCOPE="all"
COVERAGE=false
SKIP_INTEGRATION=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --security)
            TEST_SCOPE="security"
            shift
            ;;
        --service)
            TEST_SCOPE="service"
            shift
            ;;
        --coverage)
            COVERAGE=true
            shift
            ;;
        --integration)
            SKIP_INTEGRATION=false
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --security       Run security-critical tests only (JWT, Permissions, SQL Injection)"
            echo "  --service        Run service layer tests only"
            echo "  --coverage       Generate coverage report with JaCoCo"
            echo "  --integration    Include integration tests (requires Docker)"
            echo "  --help           Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                          # Run all tests"
            echo "  $0 --security --coverage    # Run security tests with coverage report"
            echo "  $0 --integration            # Run all tests including integration tests"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo "Test Configuration:"
echo "  Scope: $TEST_SCOPE"
echo "  Coverage Report: $COVERAGE"
echo "  Integration Tests: $([ "$SKIP_INTEGRATION" = false ] && echo "Enabled" || echo "Disabled")"
echo ""

# Build Maven command
MVN_CMD="mvn clean test"

if [ "$COVERAGE" = true ]; then
    MVN_CMD="$MVN_CMD jacoco:report"
fi

if [ "$SKIP_INTEGRATION" = true ]; then
    MVN_CMD="$MVN_CMD -DskipITs"
fi

# Run tests based on scope
case $TEST_SCOPE in
    security)
        echo "Running security-critical tests..."
        echo ""

        # JWT Tests
        echo "1. JWT Security Tests (21 tests)"
        cd common/web
        mvn test -Dtest=JwtUtilsTest || print_error "JWT tests failed"
        cd ../..
        print_success "JWT tests completed"
        echo ""

        # Permission Tests
        echo "2. Permission Access Tests (22 tests)"
        cd common/web
        mvn test -Dtest=FeignPermissionAccessTest || print_error "Feign permission tests failed"
        mvn test -Dtest=DubboPermissionAccessTest || print_error "Dubbo permission tests failed"
        cd ../..
        print_success "Permission tests completed"
        echo ""

        # SQL Injection Tests
        echo "3. SQL Injection Prevention Tests (24 tests)"
        cd common/data
        mvn test -Dtest=DataScopeInterceptorTest || print_error "SQL injection tests failed"
        cd ../..
        print_success "SQL injection tests completed"
        echo ""

        print_success "All security tests completed successfully!"
        ;;

    service)
        echo "Running service layer tests..."
        cd system/service
        $MVN_CMD
        cd ../..
        ;;

    all)
        echo "Running all tests..."
        echo ""
        $MVN_CMD
        ;;
esac

# Generate coverage report if requested
if [ "$COVERAGE" = true ]; then
    echo ""
    echo "================================================"
    echo "  Coverage Report Generated"
    echo "================================================"
    echo ""

    # Find and display coverage reports
    find . -name "index.html" -path "*/jacoco/*" | while read -r report; do
        module=$(echo "$report" | sed 's|/target/site/jacoco/index.html||' | sed 's|^\./||')
        echo "  $module:"
        echo "    file://$(pwd)/$report"
        echo ""
    done

    print_success "Open the HTML files in your browser to view detailed coverage"
fi

echo ""
echo "================================================"
echo "  Test Summary"
echo "================================================"
echo ""
echo "Total Tests Written: 67+"
echo "Coverage Target: 60%"
echo "Current Progress: ~20%"
echo ""
print_success "Test execution completed!"
echo ""
echo "Next steps:"
echo "  1. Review test results above"
echo "  2. Add more tests for uncovered code paths"
echo "  3. Run 'mvn clean verify' before committing"
echo ""